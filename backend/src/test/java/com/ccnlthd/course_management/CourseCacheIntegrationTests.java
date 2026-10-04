package com.ccnlthd.course_management;

import com.ccnlthd.course_management.dto.request.CourseRequest;
import com.ccnlthd.course_management.dto.response.CourseResponse;
import com.ccnlthd.course_management.entity.Course;
import com.ccnlthd.course_management.exception.AppException;
import com.ccnlthd.course_management.repository.CourseRepository;
import com.ccnlthd.course_management.service.CourseService;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:course_cache_demo;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "spring.sql.init.mode=never",
        "app.cache.course-detail-ttl=5s"
})
@Testcontainers
class CourseCacheIntegrationTests {

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", REDIS::getFirstMappedPort);
    }

    @Autowired
    private CourseService courseService;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private Statistics statistics;

    @BeforeEach
    void resetState() {
        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        Set<String> keys = redis.keys("standaloneCourseDetails::*");
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
        courseRepository.deleteAll();
        statistics.clear();
    }

    @Test
    void missWritesReadableJsonAndHitSkipsDatabase() {
        CourseResponse created = courseService.createCourse(request("Initial title"));
        String key = key(created.getId());

        statistics.clear();
        CourseResponse first = courseService.getCourseById(created.getId());
        assertEquals("Initial title", first.getTitle());
        long missSql = statistics.getPrepareStatementCount();
        assertEquals(1, missSql);
        awaitKey(key);

        String json = redis.opsForValue().get(key);
        assertNotNull(json);
        assertTrue(json.startsWith("{"));
        assertTrue(json.contains("\"title\":\"Initial title\""));
        long remainingTtlMs = redis.getExpire(key, TimeUnit.MILLISECONDS);
        assertTrue(remainingTtlMs > 0 && remainingTtlMs <= 5000);
        assertTrue(redis.expire(key, Duration.ofSeconds(30)));

        statistics.clear();
        CourseResponse second = courseService.getCourseById(created.getId());
        assertEquals(first.getId(), second.getId());
        assertEquals(first.getTitle(), second.getTitle());
        assertEquals(first.getPrice(), second.getPrice());
        assertEquals(0, statistics.getPrepareStatementCount());
        System.out.printf("demo cache miss/hit: key=%s, missSql=%d, hitSql=%d, ttlMs=%d, json=%s%n",
                key, missSql, statistics.getPrepareStatementCount(), remainingTtlMs, json);
    }

    @Test
    void expiredEntryIsReloadedFromDatabase() throws InterruptedException {
        CourseResponse created = courseService.createCourse(request("Expires"));
        String key = key(created.getId());
        courseService.getCourseById(created.getId());
        awaitKey(key);

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (Boolean.TRUE.equals(redis.hasKey(key)) && System.nanoTime() < deadline) {
            Thread.sleep(100);
        }
        assertFalse(redis.hasKey(key));

        statistics.clear();
        assertEquals("Expires", courseService.getCourseById(created.getId()).getTitle());
        assertEquals(1, statistics.getPrepareStatementCount());
        awaitKey(key);
        System.out.printf("demo cache expiry: key=%s, reloadSql=%d%n",
                key, statistics.getPrepareStatementCount());
    }

    @Test
    void successfulUpdateAndDeleteEvictDetailAfterCommit() {
        CourseResponse created = courseService.createCourse(request("Before"));
        Long id = created.getId();
        String key = key(id);
        courseService.getCourseById(id);
        awaitKey(key);
        assertTrue(redis.expire(key, Duration.ofSeconds(30)));

        courseService.updateCourse(id, request("After"));
        assertFalse(redis.hasKey(key));
        statistics.clear();
        assertEquals("After", courseService.getCourseById(id).getTitle());
        assertEquals(1, statistics.getPrepareStatementCount());
        awaitKey(key);
        assertTrue(redis.expire(key, Duration.ofSeconds(30)));

        courseService.deleteCourse(id);
        assertFalse(redis.hasKey(key));
        assertThrows(AppException.class, () -> courseService.getCourseById(id));
        System.out.printf("demo cache update/delete: key=%s, updatedTitle=After, deletedKey=%s%n",
                key, redis.hasKey(key));
    }

    @Test
    void rolledBackUpdateRetainsOldResponse() {
        CourseResponse created = courseService.createCourse(request("Before rollback"));
        String key = key(created.getId());
        courseService.getCourseById(created.getId());
        awaitKey(key);
        assertTrue(redis.expire(key, Duration.ofSeconds(30)));

        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        assertThrows(IllegalStateException.class, () -> transaction.execute(status -> {
            courseService.updateCourse(created.getId(), request("Rolled back"));
            throw new IllegalStateException("Force rollback");
        }));

        assertTrue(redis.hasKey(key));
        assertEquals("Before rollback", courseService.getCourseById(created.getId()).getTitle());
    }

    @Test
    void outerTransactionEvictsOnlyAfterCommit() {
        CourseResponse created = courseService.createCourse(request("Before commit"));
        String key = key(created.getId());
        courseService.getCourseById(created.getId());
        awaitKey(key);
        assertTrue(redis.expire(key, Duration.ofSeconds(30)));

        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(status -> {
            courseService.updateCourse(created.getId(), request("After commit"));
            assertTrue(redis.hasKey(key));
        });

        assertFalse(redis.hasKey(key));
        assertEquals("After commit", courseService.getCourseById(created.getId()).getTitle());
    }

    private void awaitKey(String key) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (!Boolean.TRUE.equals(redis.hasKey(key)) && System.nanoTime() < deadline) {
            try {
                Thread.sleep(25);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while waiting for Redis cache entry", exception);
            }
        }
        assertTrue(Boolean.TRUE.equals(redis.hasKey(key)), "Course detail must be written to Redis");
    }

    private static String key(Long id) {
        return "standaloneCourseDetails::" + id;
    }

    private static CourseRequest request(String title) {
        CourseRequest request = new CourseRequest();
        request.setTitle(title);
        request.setDescription("Redis cache integration test");
        request.setPrice(new BigDecimal("499000.00"));
        request.setLevel("BEGINNER");
        request.setStatus("PUBLISHED");
        return request;
    }
}
