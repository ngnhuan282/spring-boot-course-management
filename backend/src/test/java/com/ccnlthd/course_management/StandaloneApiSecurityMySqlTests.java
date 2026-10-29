package com.ccnlthd.course_management;

import com.ccnlthd.course_management.dto.request.CourseRequest;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "spring.flyway.enabled=true", "spring.flyway.locations=classpath:db/migration",
        "spring.flyway.baseline-on-migrate=false", "spring.flyway.validate-on-migrate=true",
        "spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.defer-datasource-initialization=false",
        "spring.sql.init.mode=never"
})
@ActiveProfiles("security")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class StandaloneApiSecurityMySqlTests {
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Container
    static final GenericContainer<?> MYSQL = new GenericContainer<>("mysql:8.4")
            .withExposedPorts(3306)
            .withEnv("MYSQL_DATABASE", "security_standalone")
            .withEnv("MYSQL_USER", "security_user")
            .withEnv("MYSQL_PASSWORD", "security_password")
            .withEnv("MYSQL_ROOT_PASSWORD", "security_root_password")
            .waitingFor(Wait.forSuccessfulCommand("MYSQL_PWD=security_password mysql --protocol=TCP"
                    + " --host=127.0.0.1 --user=security_user --database=security_standalone --execute=\"SELECT 1\"")
                    .withStartupTimeout(Duration.ofMinutes(5)));

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4-alpine")
            .withExposedPorts(6379).waitingFor(Wait.forSuccessfulCommand("redis-cli ping"));

    @DynamicPropertySource
    static void endpoints(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> "jdbc:mysql://" + MYSQL.getHost() + ":"
                + MYSQL.getMappedPort(3306) + "/security_standalone?allowPublicKeyRetrieval=true&useSSL=false");
        properties.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        properties.add("spring.datasource.username", () -> "security_user");
        properties.add("spring.datasource.password", () -> "security_password");
        properties.add("spring.data.redis.host", REDIS::getHost);
        properties.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        properties.add("spring.data.redis.url", () -> "redis://" + REDIS.getHost() + ":"
                + REDIS.getMappedPort(6379) + "/0");
        properties.add("spring.data.redis.database", () -> 0);
        properties.add("spring.data.redis.ssl.enabled", () -> false);
    }

    @Autowired WebApplicationContext context;
    @Autowired DataSource dataSource;
    private MockMvc mvc;
    private long controlId;

    @BeforeEach
    void fixture() throws Exception {
        try (var connection = dataSource.getConnection()) {
            assertEquals("MySQL", connection.getMetaData().getDatabaseProductName());
            assertEquals("security_standalone", connection.getCatalog());
            assertTrue(connection.getAutoCommit());
            System.out.printf("SECURITY_ENV database=%s version=%s catalog=%s JVM=%s Spring=%s%n",
                    connection.getMetaData().getDatabaseProductName(),
                    connection.getMetaData().getDatabaseProductVersion(), connection.getCatalog(),
                    System.getProperty("java.runtime.version"), SpringVersion.getVersion());
        }
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        controlId = http(post("/api/courses").contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(request("Security control", "Normal description"))), 201)
                .path("id").asLong();
        assertTrue(controlId > 0);
    }

    @Test
    void crudStoresSqlLikeTextWithoutExecutingIt() throws Exception {
        assertCourse(controlId, request("Security control", "Normal description"),
                http(get("/api/courses/{id}", controlId), 200));
        var before = snapshot(); // Includes Flyway seed and this test's POST-created control.
        CourseRequest payload = request("' OR 1=1 -- ", "x' UNION SELECT 1 -- ");
        JsonNode created = http(post("/api/courses").contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(payload)), 201);
        long id = created.path("id").asLong();
        assertTrue(id > 0);
        assertTrue(before.stream().noneMatch(row -> row.get("id").equals(id)));
        assertCourse(id, payload, created);
        assertCourse(id, payload, http(get("/api/courses/{id}", id), 200));
        var inserted = snapshot();
        assertEquals(before.size() + 1, inserted.size());
        assertEquals(before, withoutCourse(inserted, id));

        payload.setTitle("x'); DROP TABLE courses; -- ");
        assertCourse(id, payload, http(put("/api/courses/{id}", id).contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(payload)), 200));
        assertCourse(id, payload, http(get("/api/courses/{id}", id), 200));
        var updated = snapshot();
        assertEquals(inserted.size(), updated.size());
        assertEquals(withoutCourse(inserted, id), withoutCourse(updated, id));
        var originalRow = courseRow(inserted, id);
        var updatedRow = courseRow(updated, id);
        for (String allowed : List.of("title", "updated_at")) {
            originalRow.remove(allowed);
            updatedRow.remove(allowed);
        }
        assertEquals(originalRow, updatedRow);
        System.out.println("SECURITY_DB CRUD: one INSERT/UPDATE; control, seed and other columns unchanged");
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
        // Spring 7.0.9 skips CORS processing without configuration; HTTP 200 grants no CORS rights.
        assertAll("preflight headers and committed data",
                () -> assertEquals(200, response.getStatus()),
                () -> assertNull(response.getHeader("Access-Control-Allow-Origin")),
                () -> assertNull(response.getHeader("Access-Control-Allow-Credentials")),
                () -> assertUnchanged(before));
    }

    @Test
    void htmlTextIsReturnedAsJsonData() throws Exception {
        CourseRequest payload = request("HTML text", "<script>alert('api-security')</script><b>sample</b>");
        JsonNode created = http(post("/api/courses").contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(payload)), 201);
        long id = created.path("id").asLong();
        assertTrue(id > 0);
        assertCourse(id, payload, created);
        assertCourse(id, payload, http(get("/api/courses/{id}", id), 200));
        System.out.println("SECURITY_DB HTML equals parsed JSON; MockMvc does not execute JavaScript");
    }

    private List<Map<String, Object>> snapshot() throws Exception {
        // No test transaction: borrow a new connection after each HTTP operation has committed.
        try (var connection = dataSource.getConnection()) {
            return new JdbcTemplate(new SingleConnectionDataSource(connection, true))
                    .queryForList("SELECT * FROM courses ORDER BY id");
        }
    }

    private void assertUnchanged(List<Map<String, Object>> before) throws Exception {
        assertEquals(before, snapshot());
        System.out.println("SECURITY_DB full committed courses snapshot unchanged");
    }

    private List<Map<String, Object>> withoutCourse(List<Map<String, Object>> rows, long id) {
        return rows.stream().filter(row -> !row.get("id").equals(id)).toList();
    }

    private Map<String, Object> courseRow(List<Map<String, Object>> rows, long id) {
        return new LinkedHashMap<>(rows.stream().filter(row -> row.get("id").equals(id))
                .findFirst().orElseThrow());
    }

    private void assertCourse(long id, CourseRequest expected, JsonNode response) throws Exception {
        var row = courseRow(snapshot(), id);
        assertEquals(id, response.path("id").asLong());
        for (var field : Map.of("title", expected.getTitle(), "description", expected.getDescription(),
                "level", expected.getLevel(), "status", expected.getStatus()).entrySet()) {
            assertEquals(field.getValue(), response.path(field.getKey()).asText());
            assertEquals(field.getValue(), row.get(field.getKey()));
        }
        assertEquals(0, expected.getPrice().compareTo(response.path("price").decimalValue()));
        assertEquals(0, expected.getPrice().compareTo((BigDecimal) row.get("price")));
        System.out.printf("SECURITY_DB id=%d committed fields match JSON/request: %s%n", id,
                JSON.writeValueAsString(expected));
    }

    private CourseRequest request(String title, String description) {
        CourseRequest request = new CourseRequest();
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
        var result = mvc.perform(origin(request)).andDo(print()).andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials")).andReturn();
        return JSON.readTree(result.getResponse().getContentAsString());
    }
}
