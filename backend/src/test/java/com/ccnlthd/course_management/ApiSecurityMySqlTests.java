package com.ccnlthd.course_management;

import com.ccnlthd.course_management.dto.request.CourseRequest;
import com.ccnlthd.course_management.entity.Category;
import com.ccnlthd.course_management.entity.Course;
import com.ccnlthd.course_management.repository.CategoryRepository;
import com.ccnlthd.course_management.repository.CourseRepository;
import com.ccnlthd.course_management.service.CourseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.SpringVersion;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "spring.flyway.enabled=true", "spring.flyway.locations=classpath:db/migration",
        "spring.flyway.baseline-on-migrate=false", "spring.flyway.validate-on-migrate=true",
        "spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.defer-datasource-initialization=false",
        "spring.sql.init.mode=never", "spring.cache.type=redis"
})
@ActiveProfiles("security")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ApiSecurityMySqlTests {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final List<String> SQL_TEXT = List.of("' OR 1=1 -- ", "x' UNION SELECT 1 -- ");
    private static final List<String> TABLES = List.of("enrollments", "courses", "students", "categories");

    @Container
    static GenericContainer<?> mysql = new GenericContainer<>("mysql:8.4")
            .withExposedPorts(3306)
            .withEnv("MYSQL_DATABASE", "security_develop")
            .withEnv("MYSQL_USER", "security_user")
            .withEnv("MYSQL_PASSWORD", "security_password")
            .withEnv("MYSQL_ROOT_PASSWORD", "security_root_password")
            .waitingFor(Wait.forSuccessfulCommand("MYSQL_PWD=security_password mysql --protocol=TCP"
                    + " --host=127.0.0.1 --user=security_user --database=security_develop --execute=\"SELECT 1\"")
                    .withStartupTimeout(Duration.ofMinutes(5)));

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7.4-alpine")
            .withExposedPorts(6379).waitingFor(Wait.forSuccessfulCommand("redis-cli ping"));

    @DynamicPropertySource
    static void endpoints(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> "jdbc:mysql://" + mysql.getHost() + ":"
                + mysql.getMappedPort(3306) + "/security_develop?allowPublicKeyRetrieval=true&useSSL=false");
        properties.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        properties.add("spring.datasource.username", () -> "security_user");
        properties.add("spring.datasource.password", () -> "security_password");
        properties.add("spring.data.redis.host", redis::getHost);
        properties.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        properties.add("spring.data.redis.url", () -> "redis://" + redis.getHost() + ":"
                + redis.getMappedPort(6379) + "/0");
        properties.add("spring.data.redis.database", () -> 0);
        properties.add("spring.data.redis.ssl.enabled", () -> false);
    }

    @Autowired WebApplicationContext context;
    @Autowired DataSource dataSource;
    @Autowired CategoryRepository categories;
    @Autowired CourseRepository courses;
    @Autowired CourseService service;
    private MockMvc mvc;
    private Long categoryId;
    private Long firstId;
    private Long secondId;

    @BeforeEach
    void fixtures() throws Exception {
        try (var connection = dataSource.getConnection()) {
            assertEquals("MySQL", connection.getMetaData().getDatabaseProductName());
            assertEquals("security_develop", connection.getCatalog());
            assertTrue(connection.getAutoCommit());
            System.out.printf("SECURITY_DB product=%s version=%s catalog=%s JVM=%s Spring=%s%n",
                    connection.getMetaData().getDatabaseProductName(),
                    connection.getMetaData().getDatabaseProductVersion(), connection.getCatalog(),
                    System.getProperty("java.runtime.version"), SpringVersion.getVersion());
        }
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        for (String table : TABLES) jdbc.update("DELETE FROM " + table);
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        categoryId = category("Backend");
        firstId = service.createCourse(request(categoryId, "Java basics", "JPA introduction")).getId();
        secondId = service.createCourse(request(category("Design"), "Drawing", "Color practice")).getId();
        jdbc.update("INSERT INTO students(full_name,email,created_at) VALUES (?,?,CURRENT_TIMESTAMP(6))",
                "Control student", "security@example.test");
        Long studentId = jdbc.queryForObject("SELECT id FROM students WHERE email=?", Long.class,
                "security@example.test");
        jdbc.update("INSERT INTO enrollments(student_id,course_id,enrolled_at,status)"
                + " VALUES (?,?,CURRENT_TIMESTAMP(6),?)", studentId, firstId, "ACTIVE");
    }

    @Test
    void keywordQueryTreatsSqlSyntaxAsData() throws Exception {
        query(courses::searchByKeyword, "searchByKeyword", "Java", List.of(firstId));
        query(courses::searchByKeyword, "searchByKeyword", "no matching keyword", List.of());
        for (String text : SQL_TEXT) query(courses::searchByKeyword, "searchByKeyword", text, List.of());
        Long literalId = service.createCourse(request(categoryId, SQL_TEXT.getFirst(), "Literal SQL text")).getId();
        query(courses::searchByKeyword, "searchByKeyword", SQL_TEXT.getFirst(), List.of(literalId));
    }

    @Test
    void categoryQueryTreatsSqlSyntaxAsData() throws Exception {
        query(courses::findByCategoryName, "findByCategoryName", "Backend", List.of(firstId));
        query(courses::findByCategoryName, "findByCategoryName", "Design", List.of(secondId));
        for (String text : SQL_TEXT) query(courses::findByCategoryName, "findByCategoryName", text, List.of());
        Long literalId = service.createCourse(request(category(SQL_TEXT.getFirst()), "Literal category", "Data")).getId();
        query(courses::findByCategoryName, "findByCategoryName", SQL_TEXT.getFirst(), List.of(literalId));
    }

    @Test
    void crudStoresSqlLikeTextWithoutExecutingIt() throws Exception {
        var before = snapshot();
        String title = SQL_TEXT.getFirst();
        JsonNode created = http(post("/api/courses").contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(request(categoryId, title, "SQL text"))), 201);
        long id = created.path("id").asLong();
        assertTrue(id > 0);
        assertTrue(before.get("courses").stream().noneMatch(row -> row.get("id").equals(id)));
        assertEquals(title, created.path("title").asText());
        assertEquals(title, http(get("/api/courses/{id}", id), 200).path("title").asText());
        var inserted = snapshot();
        assertEquals(before.get("courses").size() + 1, inserted.get("courses").size());
        assertEquals(before, withoutCourse(inserted, id));
        var originalRow = courseRow(inserted, id);
        assertEquals(title, originalRow.get("title"));
        assertEquals(categoryId, originalRow.get("category_id"));
        assertEquals("SQL text", originalRow.get("description"));
        assertEquals(new BigDecimal("100000.00"), originalRow.get("price"));
        assertEquals("BEGINNER", originalRow.get("level"));
        assertEquals("PUBLISHED", originalRow.get("status"));

        title = "x'); DROP TABLE courses; -- ";
        assertEquals(title, http(put("/api/courses/{id}", id).contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(request(categoryId, title, "SQL text"))), 200).path("title").asText());
        assertEquals(title, http(get("/api/courses/{id}", id), 200).path("title").asText());
        var updated = snapshot(); // A fresh JDBC connection sees committed rows, including the control course.
        assertEquals(inserted.get("courses").size(), updated.get("courses").size());
        assertEquals(withoutCourse(inserted, id), withoutCourse(updated, id));
        var updatedRow = courseRow(updated, id);
        assertEquals(title, updatedRow.get("title"));
        originalRow.remove("title");
        originalRow.remove("updated_at");
        updatedRow.remove("title");
        updatedRow.remove("updated_at");
        assertEquals(originalRow, updatedRow);
        System.out.println("SECURITY_DB CRUD: exactly one INSERT/UPDATE; other rows and target columns unchanged");
    }

    @Test
    void crossOriginGetDoesNotGrantBrowserAccess() throws Exception {
        for (String origin : List.of("", "https://api.example", "https://outside.example")) {
            var before = snapshot();
            var request = get("/api/courses");
            if (!origin.isEmpty()) request.header("Origin", origin);
            assertTrue(http(request, 200).isArray());
            assertUnchanged(before);
        }
    }

    @Test
    void crossOriginPreflightDoesNotGrantAccess() throws Exception {
        var before = snapshot();
        var response = mvc.perform(origin(options("/api/courses"))
                        .header("Origin", "https://outside.example")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type"))
                .andDo(print()).andReturn().getResponse();
        assertAll("preflight policy and committed data",
                () -> assertEquals(200, response.getStatus()),
                () -> assertNull(response.getHeader("Access-Control-Allow-Origin")),
                () -> assertNull(response.getHeader("Access-Control-Allow-Credentials")),
                () -> assertUnchanged(before));
    }

    @Test
    void htmlTextIsReturnedAsJsonData() throws Exception {
        String html = "<script>alert('api-security')</script><b>sample</b>";
        JsonNode created = http(post("/api/courses").contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(request(categoryId, "HTML text", html))), 201);
        assertEquals(html, created.path("description").asText());
        long id = created.path("id").asLong();
        assertTrue(id > 0);
        assertEquals(html, http(get("/api/courses/{id}", id), 200).path("description").asText());
        assertEquals(html, courseRow(snapshot(), id).get("description"));
        System.out.println("SECURITY_DB HTML description equals parsed JSON; MockMvc does not execute JavaScript");
    }

    private void query(Function<String, List<Course>> query, String name, String argument,
                       List<Long> expected) throws Exception {
        var before = snapshot();
        var ids = query.apply(argument).stream().map(Course::getId).sorted().toList();
        System.out.printf("SECURITY_REPOSITORY %s argument=[%s] expected=%s actual=%s%n", name, argument, expected, ids);
        assertEquals(expected, ids);
        assertUnchanged(before);
    }

    private Map<String, List<Map<String, Object>>> snapshot() throws Exception {
        // No test transaction: each snapshot borrows a new connection after service/repository commit.
        try (var connection = dataSource.getConnection()) {
            JdbcTemplate jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
            Map<String, List<Map<String, Object>>> result = new LinkedHashMap<>();
            for (String table : TABLES) result.put(table, jdbc.queryForList("SELECT * FROM " + table + " ORDER BY id"));
            return result;
        }
    }

    private void assertUnchanged(Map<String, List<Map<String, Object>>> before) throws Exception {
        assertEquals(before, snapshot());
        System.out.println("SECURITY_DB full committed snapshot unchanged: " + TABLES);
    }

    private Map<String, List<Map<String, Object>>> withoutCourse(Map<String, List<Map<String, Object>>> data, long id) {
        var result = new LinkedHashMap<>(data);
        result.put("courses", data.get("courses").stream().filter(row -> !row.get("id").equals(id)).toList());
        return result;
    }

    private Map<String, Object> courseRow(Map<String, List<Map<String, Object>>> data, long id) {
        return new LinkedHashMap<>(data.get("courses").stream()
                .filter(row -> row.get("id").equals(id)).findFirst().orElseThrow());
    }

    private Long category(String name) {
        Category category = new Category();
        category.setName(name);
        return categories.saveAndFlush(category).getId();
    }

    private CourseRequest request(Long category, String title, String description) {
        CourseRequest request = new CourseRequest();
        request.setCategoryId(category);
        request.setTitle(title);
        request.setDescription(description);
        request.setPrice(new BigDecimal("100000"));
        request.setLevel("BEGINNER");
        request.setStatus("PUBLISHED");
        return request;
    }

    private MockHttpServletRequestBuilder origin(MockHttpServletRequestBuilder request) {
        return request.secure(true).with(servlet -> {
            servlet.setScheme("https");
            servlet.setServerName("api.example");
            servlet.setServerPort(443);
            return servlet;
        });
    }

    private JsonNode http(MockHttpServletRequestBuilder request, int status) throws Exception {
        MvcResult result = mvc.perform(origin(request)).andDo(print()).andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials")).andReturn();
        return JSON.readTree(result.getResponse().getContentAsString());
    }
}
