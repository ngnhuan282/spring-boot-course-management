package com.ccnlthd.course_management;

import com.ccnlthd.course_management.config.CourseConsistencyGate;
import com.ccnlthd.course_management.dto.request.CategoryRequest;
import com.ccnlthd.course_management.dto.request.CourseRequest;
import com.ccnlthd.course_management.service.CategoryService;
import com.ccnlthd.course_management.service.CourseService;
import com.ccnlthd.course_management.support.Task10Harness;
import com.ccnlthd.course_management.support.Task10Environment;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.aop.Advisor;
import org.springframework.aop.framework.Advised;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.interceptor.CacheInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import static com.ccnlthd.course_management.support.Task10Harness.JSON;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "spring.cache.type=redis", "spring.cache.redis.time-to-live=10m",
        "spring.flyway.enabled=true", "spring.flyway.locations=classpath:db/migration",
        "spring.flyway.baseline-on-migrate=false", "spring.flyway.validate-on-migrate=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.defer-datasource-initialization=false",
        "spring.jpa.open-in-view=false", "spring.sql.init.mode=never",
        "spring.datasource.hikari.maximum-pool-size=8",
        "spring.datasource.hikari.connection-timeout=10000",
        "logging.level.org.hibernate.orm.jdbc.bind=TRACE"
})
@Import(Task10ConcurrentCacheTests.TraceConfiguration.class)
@ActiveProfiles("task10")
@Execution(ExecutionMode.SAME_THREAD)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class Task10ConcurrentCacheTests {
    @TestConfiguration(proxyBeanMethods = false)
    static class TraceConfiguration {
        @Bean(destroyMethod = "close")
        Task10Environment task10Environment() { return Task10Environment.create(); }
        @Bean DynamicPropertyRegistrar task10Endpoints(Task10Environment environment) {
            return environment::register;
        }
        @Bean Task10Harness task10Harness(Task10Environment environment) {
            return new Task10Harness(environment);
        }
        @Bean HibernatePropertiesCustomizer task10Inspector(Task10Harness harness) {
            return properties -> properties.put("hibernate.session_factory.statement_inspector", harness);
        }
    }

    @DynamicPropertySource
    static void guardProperty(DynamicPropertyRegistry registry) {
        // Infrastructure advisors can create the gate before registrar beans run.
        // Register its flag before context refresh, including external Baseline=false.
        registry.add("course.cache.concurrent-guard-enabled",
                () -> System.getenv().getOrDefault("T10_GUARD_ENABLED", "true"));
    }

    @Autowired WebApplicationContext context;
    @Autowired CourseService courseService;
    @Autowired CategoryService categoryService;
    @Autowired CourseConsistencyGate gate;
    @Autowired @Qualifier("courseConsistencyAdvisor") Advisor productionAdvisor;
    @Autowired StringRedisTemplate redis;
    @Autowired JdbcTemplate jdbc;
    @Autowired Task10Harness h;
    @Autowired Task10Environment environment;

    private MockMvc mvc;
    private ExecutorService pool;
    private Long categoryId;
    private List<Long> ids;
    private String oldCategory;
    private boolean passed;

    @BeforeAll
    void instrumentRealProxies() {
        assertEquals(environment.guardEnabled(), gate.isEnabled(),
                "The production guard must match the selected Task 10 environment");
        assertAdvisorOrder(courseService, true);
        assertAdvisorOrder(categoryService, false);
        h.install(courseService, jdbc);
        h.install(categoryService, jdbc);
    }

    private void assertAdvisorOrder(Object service, boolean cached) {
        assertInstanceOf(Advised.class, service);
        Advisor[] advisors = ((Advised) service).getAdvisors();
        int gateIndex = Arrays.asList(advisors).indexOf(productionAdvisor);
        assertTrue(gateIndex >= 0, "Production consistency advisor was not installed");
        boolean transactionFound = false, cacheFound = false;
        for (int i = 0; i < advisors.length; i++) {
            var advice = advisors[i].getAdvice();
            if (advice instanceof TransactionInterceptor) {
                transactionFound = true;
                assertTrue(gateIndex < i, "Gate must wrap transaction advice");
            }
            if (advice instanceof CacheInterceptor) {
                cacheFound = true;
                assertTrue(gateIndex < i, "Gate must wrap cache advice");
            }
        }
        assertTrue(transactionFound);
        if (cached) assertTrue(cacheFound);
    }

    @BeforeEach
    void fixture(TestInfo info) throws Exception {
        h.reset(info.getTestMethod().orElseThrow().getName());
        passed = false;
        ids = null;
        categoryId = null;
        h.event("ADVISOR_CHAIN", Arrays.stream(((Advised) courseService).getAdvisors())
                .map(a -> a.getAdvice().getClass().getName()).toList());
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        pool = Executors.newFixedThreadPool(2);
        oldCategory = "T10 old " + UUID.randomUUID();
        CategoryRequest category = categoryRequest(oldCategory);
        categoryId = categoryService.createCategory(category).getId();
        ids = List.of(courseService.createCourse(courseRequest("T10 Course A")).getId(),
                courseService.createCourse(courseRequest("T10 Course B")).getId());
        request("GET", coursePath(ids.get(0)), "");
        request("GET", coursePath(ids.get(1)), "");
        h.capture("fixture-two-courses-cached", categoryId, ids, redis);
    }

    @AfterEach
    void saveEvenWhenAssertionFails() throws Exception {
        h.releaseAll();
        if (pool != null) {
            pool.shutdown();
            if (!pool.awaitTermination(Task10Harness.WAIT_SECONDS, TimeUnit.SECONDS)) {
                pool.shutdownNow();
                if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                    throw new AssertionError("Workers did not stop; evidence is incomplete");
                }
            }
        }
        if (categoryId != null && ids != null) {
            h.capture("after-test", categoryId, ids, redis);
        }
        h.write(passed, gate.isEnabled());
    }

    @AfterAll
    void removeTestProbes() {
        h.uninstall();
    }

    @Test
    void twoPutsSameCourseLastAppliedCommitWins() throws Exception {
        var t1Pause = h.pause("T1", "updateCourse");
        var t2Pause = h.pause("T2", "updateCourse");
        Future<MvcResult> t1 = submit("T1", "PUT", coursePath(ids.get(0)),
                JSON.writeValueAsString(courseRequest("T10 T1")));
        h.awaitReady(t1Pause);
        Future<MvcResult> t2 = submit("T2", "PUT", coursePath(ids.get(0)),
                JSON.writeValueAsString(courseRequest("T10 T2")));
        h.awaitEntry("T2");
        if (gate.isEnabled()) {
            awaitQueued("T2", t2);
        } else {
            h.awaitReady(t2Pause);
        }
        h.capture("both-requests-in-flight-before-t1-commit", categoryId, ids, redis);
        assertEquals("T10 Course A", committedTitle(ids.get(0)));
        h.release(t1Pause);
        assertEquals("T10 T1", body(finish(t1)).path("title").asString());
        h.awaitReady(t2Pause);
        h.capture("t1-committed-t2-not-committed", categoryId, ids, redis);
        assertEquals("T10 T1", committedTitle(ids.get(0)));
        assertFalse(Boolean.TRUE.equals(redis.hasKey(key(ids.get(0)))));
        assertTrue(Boolean.TRUE.equals(redis.hasKey(key(ids.get(1)))));
        h.release(t2Pause);
        assertEquals("T10 T2", body(finish(t2)).path("title").asString());
        h.capture("both-puts-finished-before-verification-get", categoryId, ids, redis);
        assertEquals("T10 T2", committedTitle(ids.get(0)));
        assertFalse(Boolean.TRUE.equals(redis.hasKey(key(ids.get(0)))));
        verifyFinalState();
        passed = true;
    }

    @Test
    void categoryRenameAgainstInFlightMissKeepsBothCoursesConsistent() throws Exception {
        overlapReadAndWrite(true);
        passed = true;
    }

    @Test
    void courseUpdateAgainstInFlightMissDoesNotPublishOldTitle() throws Exception {
        overlapReadAndWrite(false);
        passed = true;
    }

    private void overlapReadAndWrite(boolean categoryChange) throws Exception {
        Long a = ids.get(0);
        redis.delete(key(a)); // Prepare a real MISS; Course B remains cached.
        var readerPause = h.pause("R", "getCourseById");
        Future<MvcResult> reader = submit("R", "GET", coursePath(a), "");
        h.awaitReady(readerPause); // DTO fully contains the OLD title/category.
        h.capture("reader-holds-old-dto-before-publication", categoryId, ids, redis);
        String newCategory = "T10 renamed " + UUID.randomUUID();
        String method = "PUT";
        String path = categoryChange ? "/api/categories/" + categoryId : coursePath(a);
        String request = JSON.writeValueAsString(categoryChange
                ? categoryRequest(newCategory) : courseRequest("T10 Updated A"));
        Future<MvcResult> writer = submit("W", method, path, request);
        h.awaitEntry("W");
        if (gate.isEnabled()) {
            awaitQueued("W", writer);
            h.capture("writer-queued-while-reader-holds-gate", categoryId, ids, redis);
            h.release(readerPause);
            assertOldInFlightResponse(finish(reader));
            finish(writer);
        } else {
            finish(writer); // Force commit + eviction BEFORE the old DTO can be cached.
            h.capture("writer-committed-reader-still-holds-old-dto", categoryId, ids, redis);
            h.release(readerPause);
            assertOldInFlightResponse(finish(reader));
        }
        // Critical: inspect raw Redis BEFORE sending a GET that could conceal the race.
        var after = h.capture("both-finished-before-verification-get", categoryId, ids, redis);
        if (categoryChange) {
            assertEquals(newCategory, categoryName(after));
        } else {
            assertEquals("T10 Updated A", committedTitle(a));
            assertTrue(Boolean.TRUE.equals(redis.hasKey(key(ids.get(1)))));
        }
        assertCachesAgreeWithCommittedRowsOrAreAbsent(after);
        verifyFinalState();
    }

    private void assertOldInFlightResponse(MvcResult result) throws Exception {
        assertEquals("T10 Course A", body(result).path("title").asString());
        assertEquals(oldCategory, body(result).path("categoryName").asString());
    }

    private void awaitQueued(String actor, Future<?> result) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(Task10Harness.WAIT_SECONDS);
        while (!gate.hasQueuedThread(h.worker(actor))) {
            if (result.isDone()) {
                result.get();
                fail("Writer completed without queuing at the production gate");
            }
            if (System.nanoTime() >= deadline) fail("Writer did not queue before the deadline");
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
        }
        h.event("GATE_QUEUE_OBSERVED", Task10Harness.row("actor", actor,
                "thread_id", h.worker(actor).threadId(), "queued", true));
    }

    private Future<MvcResult> submit(String actor, String method, String path, String request) {
        return pool.submit(() -> {
            h.actor(actor);
            try { return request(method, path, request); }
            finally { h.clearActor(); }
        });
    }

    private MvcResult finish(Future<MvcResult> result) throws Exception {
        return result.get(Task10Harness.WAIT_SECONDS, TimeUnit.SECONDS);
    }

    private MvcResult request(String method, String path, String payload) throws Exception {
        MockHttpServletRequestBuilder builder = "GET".equals(method) ? get(path) : put(path);
        if (!payload.isEmpty()) builder.contentType(MediaType.APPLICATION_JSON).content(payload);
        MvcResult result = mvc.perform(builder).andReturn();
        String response = result.getResponse().getContentAsString();
        h.exchange(method, path, payload, result.getResponse().getStatus(), response);
        assertEquals(200, result.getResponse().getStatus(), "Unexpected HTTP status: " + response);
        return result;
    }

    private JsonNode body(MvcResult result) throws Exception {
        return JSON.readTree(result.getResponse().getContentAsString());
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> courses(Map<String, Object> snapshot) {
        return (List<Map<String, Object>>) ((Map<String, Object>) snapshot.get("database")).get("courses");
    }

    @SuppressWarnings("unchecked")
    private String categoryName(Map<String, Object> snapshot) {
        var db = (Map<String, Object>) snapshot.get("database");
        return ((Map<String, Object>) db.get("category")).get("name").toString();
    }

    @SuppressWarnings("unchecked")
    private String committedTitle(Long id) {
        var db = h.database(categoryId, ids);
        return ((List<Map<String, Object>>) db.get("courses")).stream()
                .filter(c -> id.equals(c.get("id"))).findFirst().orElseThrow().get("title").toString();
    }

    private void assertDtoMatchesDb(JsonNode dto, Map<String, Object> db) {
        assertEquals(((Number) db.get("id")).longValue(), dto.path("id").asLong());
        assertEquals(((Number) db.get("category_id")).longValue(), dto.path("categoryId").asLong());
        assertEquals(db.get("category_name").toString(), dto.path("categoryName").asString(),
                "Redis/API has a stale Category name");
        for (String field : List.of("title", "description", "level", "status")) {
            assertEquals(db.get(field).toString(), dto.path(field).asString(),
                    "Redis/API differs from committed DB: " + field);
        }
        assertEquals(0, new BigDecimal(db.get("price").toString())
                .compareTo(new BigDecimal(dto.path("price").asString())));
    }

    @SuppressWarnings("unchecked")
    private void assertCachesAgreeWithCommittedRowsOrAreAbsent(Map<String, Object> snapshot) {
        var states = (List<Map<String, Object>>) snapshot.get("redis");
        var rows = courses(snapshot);
        for (int i = 0; i < rows.size(); i++) {
            if (states.get(i).get("json") instanceof JsonNode json) assertDtoMatchesDb(json, rows.get(i));
        }
    }

    private void verifyFinalState() throws Exception {
        var snapshot = h.capture("before-final-reloads", categoryId, ids, redis);
        List<Map<String, Object>> rows = courses(snapshot);
        for (int i = 0; i < ids.size(); i++) {
            Long id = ids.get(i);
            JsonNode api = body(request("GET", coursePath(id), ""));
            assertDtoMatchesDb(api, rows.get(i));
            String raw = redis.opsForValue().get(key(id));
            assertNotNull(raw);
            assertDtoMatchesDb(JSON.readTree(raw), rows.get(i));
            assertEquals(api, JSON.readTree(raw));
            Long ttl = redis.getExpire(key(id), TimeUnit.MILLISECONDS);
            assertNotNull(ttl);
            assertTrue(ttl > 0 && ttl <= 600_000, "Unexpected Redis PTTL: " + ttl);
        }
        long before = h.sqlCount();
        request("GET", coursePath(ids.get(0)), "");
        assertEquals(before, h.sqlCount(), "A repeated cached GET should not execute Hibernate SQL");
        h.capture("final-api-db-redis-agree", categoryId, ids, redis);
    }

    private CategoryRequest categoryRequest(String name) {
        CategoryRequest request = new CategoryRequest();
        request.setName(name);
        request.setDescription("Task 10 concurrency fixture");
        return request;
    }

    private CourseRequest courseRequest(String title) {
        CourseRequest request = new CourseRequest();
        request.setCategoryId(categoryId);
        request.setTitle(title);
        request.setDescription("Task 10 concurrency fixture");
        request.setPrice(new BigDecimal("100.00"));
        request.setLevel("BEGINNER");
        request.setStatus("PUBLISHED");
        return request;
    }

    private String coursePath(Long id) { return "/api/courses/" + id; }
    private String key(Long id) { return "courseDetails::" + id; }
}
