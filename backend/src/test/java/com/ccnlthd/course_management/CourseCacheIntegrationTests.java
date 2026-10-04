package com.ccnlthd.course_management;

import com.ccnlthd.course_management.dto.request.CategoryRequest;
import com.ccnlthd.course_management.dto.request.CourseRequest;
import com.ccnlthd.course_management.dto.response.CourseResponse;
import com.ccnlthd.course_management.exception.AppException;
import com.ccnlthd.course_management.exception.ErrorCode;
import com.ccnlthd.course_management.service.CategoryService;
import com.ccnlthd.course_management.service.CourseService;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "spring.cache.type=redis",
        "spring.cache.redis.time-to-live=5s",
        "spring.jpa.properties.hibernate.generate_statistics=true"
})
@Testcontainers
class CourseCacheIntegrationTests {

    @Container
    @ServiceConnection(name = "redis")
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379);

    @Autowired
    private CourseService courseService;

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private Long courseId;
    private Long categoryId;
    private String categoryName;

    @BeforeEach
    void createFreshCourse() {
        categoryName = "Cache category " + UUID.randomUUID();
        CategoryRequest category = new CategoryRequest();
        category.setName(categoryName);
        categoryId = categoryService.createCategory(category).getId();
        courseId = courseService.createCourse(courseRequest("Original course")).getId();
        assertFalse(Boolean.TRUE.equals(redisTemplate.hasKey(cacheKey())));
    }

    @Test
    void missThenHitStoresReadableJsonAndAvoidsMoreSql() {
        Statistics statistics = statistics();
        statistics.clear();

        CourseResponse miss = courseService.getCourseById(courseId);
        long sqlAfterMiss = statistics.getPrepareStatementCount();
        assertTrue(sqlAfterMiss > 0, "A cache miss must read the database");
        awaitKey();

        String json = redisTemplate.opsForValue().get(cacheKey());
        assertNotNull(json, "A cache miss must create a Redis key");
        assertTrue(json.startsWith("{"), "The cached response must be readable JSON");
        assertTrue(json.contains("\"id\":" + courseId));
        assertTrue(json.contains("\"title\":\"Original course\""));
        assertTrue(json.contains("\"categoryName\":\"" + categoryName + "\""));
        Long ttlMillis = redisTemplate.getExpire(cacheKey(), TimeUnit.MILLISECONDS);
        assertNotNull(ttlMillis);
        assertTrue(ttlMillis > 0 && ttlMillis <= 5_000, "The test cache TTL must be five seconds");
        extendTtlForMutationCheck();

        CourseResponse hit = courseService.getCourseById(courseId);
        assertEquals(CourseResponse.class, hit.getClass(), "Redis must deserialize to CourseResponse");
        assertSameResponse(miss, hit);
        assertEquals(sqlAfterMiss, statistics.getPrepareStatementCount(),
                "A cache hit must not issue another SQL statement");

        System.out.printf("course cache miss/hit: key=%s, missSql=%d, hitSql=%d, ttlMs=%d, json=%s%n",
                cacheKey(), sqlAfterMiss, statistics.getPrepareStatementCount(), ttlMillis, json);
    }

    @Test
    void expiredEntryTriggersAnotherDatabaseRead() throws InterruptedException {
        courseService.getCourseById(courseId);
        awaitKey();

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (Boolean.TRUE.equals(redisTemplate.hasKey(cacheKey())) && System.nanoTime() < deadline) {
            Thread.sleep(100);
        }
        assertFalse(Boolean.TRUE.equals(redisTemplate.hasKey(cacheKey())), "Redis must expire the entry");

        Statistics statistics = statistics();
        statistics.clear();
        CourseResponse refreshed = courseService.getCourseById(courseId);
        long sqlAfterExpiry = statistics.getPrepareStatementCount();
        assertTrue(sqlAfterExpiry > 0, "An expired entry must reload from the database");
        assertEquals("Original course", refreshed.getTitle());
        awaitKey();

        System.out.printf("course cache expiry: key=%s, reloadSql=%d%n", cacheKey(), sqlAfterExpiry);
    }

    @Test
    void updatingAndDeletingCourseEvictsOnlyAfterSuccessfulCalls() {
        courseService.getCourseById(courseId);
        awaitKey();
        extendTtlForMutationCheck();

        CourseResponse updated = courseService.updateCourse(courseId, courseRequest("Updated course"));
        assertEquals("Updated course", updated.getTitle());
        assertFalse(Boolean.TRUE.equals(redisTemplate.hasKey(cacheKey())),
                "A committed update must evict the old response");

        CourseResponse reloaded = courseService.getCourseById(courseId);
        assertEquals("Updated course", reloaded.getTitle());
        awaitKey();
        extendTtlForMutationCheck();

        courseService.deleteCourse(courseId);
        assertFalse(Boolean.TRUE.equals(redisTemplate.hasKey(cacheKey())),
                "A committed delete must evict the old response");
        AppException missing = assertThrows(AppException.class,
                () -> courseService.getCourseById(courseId));
        assertEquals(ErrorCode.COURSE_NOT_FOUND, missing.getErrorCode());

        System.out.printf("course cache update/delete: key=%s, updatedTitle=%s, deletedKey=%s%n",
                cacheKey(), reloaded.getTitle(), redisTemplate.hasKey(cacheKey()));
    }

    @Test
    void renamingCategoryRefreshesCachedCategoryName() {
        CourseResponse original = courseService.getCourseById(courseId);
        assertEquals(categoryName, original.getCategoryName());
        awaitKey();
        extendTtlForMutationCheck();

        CategoryRequest rename = new CategoryRequest();
        rename.setName("Renamed " + UUID.randomUUID());
        categoryService.updateCategory(categoryId, rename);
        assertFalse(Boolean.TRUE.equals(redisTemplate.hasKey(cacheKey())),
                "Changing category name must evict cached course details");

        CourseResponse refreshed = courseService.getCourseById(courseId);
        assertEquals(rename.getName(), refreshed.getCategoryName());
        awaitKey();

        System.out.printf("course cache category rename: key=%s, before=%s, after=%s%n",
                cacheKey(), original.getCategoryName(), refreshed.getCategoryName());
    }

    @Test
    void rolledBackCourseUpdateLeavesCachedResponseIntact() {
        courseService.getCourseById(courseId);
        awaitKey();
        extendTtlForMutationCheck();

        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        assertThrows(IllegalStateException.class, () -> transaction.execute(status -> {
            courseService.updateCourse(courseId, courseRequest("Rolled back title"));
            throw new IllegalStateException("Force rollback");
        }));

        assertTrue(Boolean.TRUE.equals(redisTemplate.hasKey(cacheKey())),
                "A rolled back update must retain the existing cache entry");
        assertEquals("Original course", courseService.getCourseById(courseId).getTitle());
    }

    @Test
    void outerTransactionEvictsOnlyAfterCommit() {
        courseService.getCourseById(courseId);
        awaitKey();
        extendTtlForMutationCheck();

        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(status -> {
            courseService.updateCourse(courseId, courseRequest("Committed title"));
            assertTrue(Boolean.TRUE.equals(redisTemplate.hasKey(cacheKey())),
                    "The cached response must remain until the outer transaction commits");
        });

        assertFalse(Boolean.TRUE.equals(redisTemplate.hasKey(cacheKey())));
        assertEquals("Committed title", courseService.getCourseById(courseId).getTitle());
    }

    private CourseRequest courseRequest(String title) {
        CourseRequest request = new CourseRequest();
        request.setCategoryId(categoryId);
        request.setTitle(title);
        request.setDescription("Redis cache integration test");
        request.setPrice(new BigDecimal("149.90"));
        request.setLevel("BEGINNER");
        request.setStatus("DRAFT");
        return request;
    }

    private Statistics statistics() {
        return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }

    private String cacheKey() {
        return "courseDetails::" + courseId;
    }

    private void extendTtlForMutationCheck() {
        assertTrue(Boolean.TRUE.equals(redisTemplate.expire(cacheKey(), Duration.ofSeconds(30))));
    }

    private void awaitKey() {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (!Boolean.TRUE.equals(redisTemplate.hasKey(cacheKey())) && System.nanoTime() < deadline) {
            try {
                Thread.sleep(25);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while waiting for Redis cache entry", exception);
            }
        }
        assertTrue(Boolean.TRUE.equals(redisTemplate.hasKey(cacheKey())),
                "Course detail must be written to Redis");
    }

    private static void assertSameResponse(CourseResponse expected, CourseResponse actual) {
        assertEquals(expected.getId(), actual.getId());
        assertEquals(expected.getCategoryId(), actual.getCategoryId());
        assertEquals(expected.getCategoryName(), actual.getCategoryName());
        assertEquals(expected.getTitle(), actual.getTitle());
        assertEquals(expected.getDescription(), actual.getDescription());
        assertEquals(0, expected.getPrice().compareTo(actual.getPrice()));
        assertEquals(expected.getLevel(), actual.getLevel());
        assertEquals(expected.getStatus(), actual.getStatus());
    }
}
