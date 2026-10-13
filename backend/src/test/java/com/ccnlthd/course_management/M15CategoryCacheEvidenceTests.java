package com.ccnlthd.course_management;

import com.ccnlthd.course_management.dto.request.CategoryRequest;
import com.ccnlthd.course_management.dto.request.CourseRequest;
import com.ccnlthd.course_management.repository.CategoryRepository;
import com.ccnlthd.course_management.repository.CourseRepository;
import com.ccnlthd.course_management.service.CategoryService;
import com.ccnlthd.course_management.service.CourseService;
import com.ccnlthd.course_management.support.M15Evidence;
import com.ccnlthd.course_management.support.M15SqlTrace;
import com.ccnlthd.course_management.support.M15SqlTraceConfiguration;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.cache.type=redis",
        "spring.cache.redis.time-to-live=10m"
})
@Import(M15SqlTraceConfiguration.class)
@Testcontainers
class M15CategoryCacheEvidenceTests {
    @Container
    @ServiceConnection(name = "redis")
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379);

    @Autowired WebApplicationContext context;
    @Autowired CourseService courseService;
    @Autowired CategoryService categoryService;
    @Autowired CourseRepository courseRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired StringRedisTemplate redisTemplate;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired EntityManager entityManager;
    @Autowired M15SqlTrace sql;

    private MockMvc mvc;
    private Long categoryId;
    private String categoryName;
    private List<Long> courseIds;
    private List<Map<String, Object>> steps;
    private M15SqlTrace.Snapshot lastActionSql;

    @BeforeEach
    void createTwoCoursesInOneCategory() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        categoryName = "M15 category " + UUID.randomUUID();
        categoryId = categoryService.createCategory(categoryRequest(categoryName)).getId();
        courseIds = List.of(courseService.createCourse(courseRequest("M15 Course A")).getId(),
                courseService.createCourse(courseRequest("M15 Course B")).getId());
        steps = new ArrayList<>();
        sql.clear();
        assertFalse(exists(courseIds.get(0)));
        assertFalse(exists(courseIds.get(1)));
    }

    @Test
    void missAndHitTrackBothTablesAndCacheFullCategoryDto() throws IOException {
        Long id = courseIds.get(0);
        request(get("/api/courses/{id}", id), 200, "miss");
        M15SqlTrace.Snapshot miss = lastActionSql;
        assertTrue(miss.courseSelects() > 0);
        assertTrue(miss.categorySelects() > 0);
        assertCachedCategory(id, categoryName);
        Long ttl = redisTemplate.getExpire(key(id), TimeUnit.MILLISECONDS);
        assertNotNull(ttl);
        assertTrue(ttl > 590_000 && ttl <= 600_000);
        String originalJson = cached(id);

        request(get("/api/courses/{id}", id), 200, "hit");
        assertEquals(0, lastActionSql.selectStatements());
        assertEquals(originalJson, cached(id));
        M15Evidence.write("miss-hit", 600, steps);
    }

    @Test
    void courseUpdateDeleteEvictOnlyAffectedKeyAndGetReturns404() throws IOException {
        primeBoth();
        Long id = courseIds.get(0);
        String otherJson = cached(courseIds.get(1));
        request(put("/api/courses/{id}", id).contentType(MediaType.APPLICATION_JSON)
                .content(M15Evidence.JSON.writeValueAsString(courseRequest("M15 Updated Course"))),
                200, "course-update-after-commit");
        assertFalse(exists(id));
        assertEquals(otherJson, cached(courseIds.get(1)));
        assertEquals("M15 Updated Course", courseRepository.findById(id).orElseThrow().getTitle());

        MvcResult reload = request(get("/api/courses/{id}", id), 200, "course-update-reload");
        assertTrue(reload.getResponse().getContentAsString().contains("M15 Updated Course"));
        assertTrue(cached(id).contains("M15 Updated Course"));
        request(delete("/api/courses/{id}", id), 204, "course-delete-after-commit");
        assertFalse(exists(id));
        assertFalse(courseRepository.existsById(id));
        assertEquals(otherJson, cached(courseIds.get(1)));
        MvcResult missing = request(get("/api/courses/{id}", id), 404, "course-delete-get-404");
        assertTrue(missing.getResponse().getContentAsString().contains("COURSE_NOT_FOUND"));
        assertFalse(exists(id));
        M15Evidence.write("course-update-delete", 600, steps);
    }

    @Test
    void categoryRenameKeepsTwoKeysUntilCommitThenReloadsBothResponsesAndRedis() throws IOException {
        primeBoth();
        List<String> before = courseIds.stream().map(this::cached).toList();
        String renamed = "M15 renamed " + UUID.randomUUID();
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(status -> {
            sql.clear();
            MvcResult response = perform(put("/api/categories/{id}", categoryId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(M15Evidence.JSON.writeValueAsString(categoryRequest(renamed))), 200);
            entityManager.flush();
            assertEquals(before, courseIds.stream().map(this::cached).toList(),
                    "Category invalidation must wait for the outer transaction commit");
            capture("category-rename-before-commit", response);
        });
        sql.clear();
        capture("category-rename-after-commit", null);
        assertEquals(renamed, categoryRepository.findById(categoryId).orElseThrow().getName());
        assertTrue(courseIds.stream().noneMatch(this::exists));

        for (Long id : courseIds) {
            MvcResult result = request(get("/api/courses/{id}", id), 200, "category-rename-reload-" + id);
            assertTrue(result.getResponse().getContentAsString().contains(renamed));
            assertTrue(lastActionSql.courseSelects() > 0);
            assertTrue(lastActionSql.categorySelects() > 0);
            assertCachedCategory(id, renamed);
        }
        M15Evidence.write("category-rename-two-courses", 600, steps);
    }

    @Test
    void categoryRollbackKeepsDatabaseAndBothCachedResponsesUnchanged() throws IOException {
        primeBoth();
        List<String> before = courseIds.stream().map(this::cached).toList();
        String attempted = "M15 rollback " + UUID.randomUUID();
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        assertThrows(IllegalStateException.class, () -> transaction.executeWithoutResult(status -> {
            sql.clear();
            categoryService.updateCategory(categoryId, categoryRequest(attempted));
            entityManager.flush(); // Send UPDATE to the database before the forced rollback.
            assertEquals(before, courseIds.stream().map(this::cached).toList());
            capture("category-rollback-before-rollback", null);
            throw new IllegalStateException("M15 forced Category rollback");
        }));
        sql.clear();
        capture("category-rollback-after-rollback", null);
        assertEquals(categoryName, categoryRepository.findById(categoryId).orElseThrow().getName());
        assertEquals(before, courseIds.stream().map(this::cached).toList());
        for (Long id : courseIds) {
            MvcResult hit = request(get("/api/courses/{id}", id), 200, "category-rollback-hit-" + id);
            assertTrue(hit.getResponse().getContentAsString().contains(categoryName));
            assertFalse(hit.getResponse().getContentAsString().contains(attempted));
            assertEquals(0, lastActionSql.selectStatements());
            assertCachedCategory(id, categoryName);
        }
        M15Evidence.write("category-rollback", 600, steps);
    }

    @Test
    void deletingCategoryWithTwoCoursesReturns409AndPreservesDatabaseAndCache() throws IOException {
        primeBoth();
        List<String> before = courseIds.stream().map(this::cached).toList();
        MvcResult conflict = request(delete("/api/categories/{id}", categoryId), 409,
                "category-delete-conflict");
        assertTrue(conflict.getResponse().getContentAsString().contains("CATEGORY_HAS_COURSES"));
        assertTrue(categoryRepository.existsById(categoryId));
        assertTrue(courseIds.stream().allMatch(courseRepository::existsById));
        assertEquals(before, courseIds.stream().map(this::cached).toList());
        assertTrue(lastActionSql.statements().stream().noneMatch(statement ->
                statement.stripLeading().toLowerCase(java.util.Locale.ROOT).startsWith("delete")));
        M15Evidence.write("category-delete-conflict", 600, steps);
    }

    private void primeBoth() {
        for (Long id : courseIds) {
            request(get("/api/courses/{id}", id), 200, "prime-" + id);
            assertCachedCategory(id, categoryName);
        }
    }

    private MvcResult request(MockHttpServletRequestBuilder request, int expectedStatus, String stage) {
        sql.clear();
        MvcResult response = perform(request, expectedStatus);
        capture(stage, response);
        return response;
    }

    private MvcResult perform(MockHttpServletRequestBuilder request, int expectedStatus) {
        try {
            return mvc.perform(request).andExpect(status().is(expectedStatus)).andReturn();
        } catch (Exception exception) {
            throw new IllegalStateException("M15 HTTP request failed", exception);
        }
    }

    private void capture(String stage, MvcResult response) {
        // Freeze the action's SQL before reading DB snapshots, which are separate evidence.
        M15SqlTrace.Snapshot actionSql = sql.snapshot();
        lastActionSql = actionSql;
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("stage", stage);
        step.put("at", Instant.now().toString());
        step.put("http_status", response == null ? null : response.getResponse().getStatus());
        String body = response == null ? "" : new String(response.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        step.put("response", body.isBlank() ? null : M15Evidence.JSON.readTree(body));
        step.put("sql", actionSql);
        step.put("redis", courseIds.stream().map(id -> M15Evidence.redisState(redisTemplate, id)).toList());
        step.put("db_view_scope", TransactionSynchronizationManager.isActualTransactionActive()
                ? "inside outer transaction; uncommitted" : "committed database");
        Map<String, Object> database = new LinkedHashMap<>();
        database.put("category", categoryRepository.findById(categoryId)
                .map(category -> Map.of("id", category.getId(), "name", category.getName()))
                .orElse(null));
        database.put("courses", courseIds.stream().map(id -> courseRepository.findById(id)
                .map(course -> Map.of("id", course.getId(), "title", course.getTitle(),
                        "categoryId", course.getCategory().getId())).orElse(null)).toList());
        step.put("database", database);
        steps.add(step);
        System.out.printf("M15 | %s | HTTP=%s | SELECT Course=%d Category=%d Total=%d | keys=%s%n",
                stage, step.get("http_status"), actionSql.courseSelects(), actionSql.categorySelects(),
                actionSql.selectStatements(), courseIds.stream().map(this::key).toList());
    }

    private void assertCachedCategory(Long id, String expectedName) {
        assertTrue(exists(id));
        String json = cached(id);
        assertNotNull(json);
        assertEquals(id.longValue(), M15Evidence.JSON.readTree(json).get("id").longValue());
        assertEquals(categoryId.longValue(), M15Evidence.JSON.readTree(json).get("categoryId").longValue());
        assertTrue(json.contains("\"categoryName\":\"" + expectedName + "\""));
    }

    private boolean exists(Long id) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key(id)));
    }

    private String cached(Long id) {
        return redisTemplate.opsForValue().get(key(id));
    }

    private String key(Long id) {
        return "courseDetails::" + id;
    }

    private CategoryRequest categoryRequest(String name) {
        CategoryRequest request = new CategoryRequest();
        request.setName(name);
        request.setDescription("M15 isolated integration evidence");
        return request;
    }

    private CourseRequest courseRequest(String title) {
        CourseRequest request = new CourseRequest();
        request.setCategoryId(categoryId);
        request.setTitle(title);
        request.setDescription("M15 isolated integration evidence");
        request.setPrice(new BigDecimal("149.90"));
        request.setLevel("BEGINNER");
        request.setStatus("DRAFT");
        return request;
    }
}
