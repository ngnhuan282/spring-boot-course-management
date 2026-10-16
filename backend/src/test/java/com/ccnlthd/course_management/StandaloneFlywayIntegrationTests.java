package com.ccnlthd.course_management;

import com.ccnlthd.course_management.dto.request.CourseRequest;
import com.ccnlthd.course_management.service.CourseService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "spring.profiles.active=dev",
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.defer-datasource-initialization=false",
        "spring.sql.init.mode=never"
})
@Testcontainers
class StandaloneFlywayIntegrationTests {
    @Container
    static final GenericContainer<?> MYSQL = new GenericContainer<>(DockerImageName.parse("mysql:8.4"))
            .withEnv("MYSQL_DATABASE", "course_standalone_test")
            .withEnv("MYSQL_USER", "standalone_test")
            .withEnv("MYSQL_PASSWORD", "standalone_test_password")
            .withEnv("MYSQL_ROOT_PASSWORD", "standalone_root_test")
            .withExposedPorts(3306)
            .waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(3)));

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void connections(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:mysql://" + MYSQL.getHost() + ":"
                + MYSQL.getMappedPort(3306) + "/course_standalone_test?allowPublicKeyRetrieval=true&useSSL=false");
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.datasource.username", () -> "standalone_test");
        registry.add("spring.datasource.password", () -> "standalone_test_password");
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", REDIS::getFirstMappedPort);
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired Flyway flyway;
    @Autowired CourseService courses;
    @Autowired StringRedisTemplate redis;

    @Test
    void freshMysqlIsMigratedAndRepeatedMigrationKeepsBusinessData() {
        List<String> tables = jdbc.queryForList("SELECT TABLE_NAME FROM information_schema.TABLES "
                + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_TYPE='BASE TABLE' ORDER BY TABLE_NAME", String.class);
        assertEquals(List.of("courses", "flyway_schema_history"), tables);
        assertEquals(List.of("1", "2"), jdbc.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success=1 ORDER BY installed_rank", String.class));
        assertEquals(3L, jdbc.queryForObject("SELECT COUNT(*) FROM courses", Long.class));
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.COLUMNS "
                + "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='courses' AND COLUMN_NAME='category_id'", Long.class));

        CourseRequest request = new CourseRequest();
        request.setTitle("Standalone migration integration test");
        request.setDescription("A Course can be created without a Category.");
        request.setPrice(new BigDecimal("129000.00"));
        request.setLevel("BEGINNER");
        request.setStatus("PUBLISHED");
        var created = courses.createCourse(request);
        assertEquals(request.getTitle(), courses.getCourseById(created.getId()).getTitle());
        assertTrue(Boolean.TRUE.equals(redis.hasKey("standaloneCourseDetails::" + created.getId())));

        List<Map<String, Object>> before = jdbc.queryForList(
                "SELECT version,checksum,installed_on,success FROM flyway_schema_history ORDER BY installed_rank");
        assertTrue(flyway.validateWithResult().validationSuccessful);
        assertEquals(0, flyway.migrate().migrationsExecuted);
        assertEquals(before, jdbc.queryForList(
                "SELECT version,checksum,installed_on,success FROM flyway_schema_history ORDER BY installed_rank"));
        assertEquals(4L, jdbc.queryForObject("SELECT COUNT(*) FROM courses", Long.class));
        assertFalse(courses.getAllCourses().isEmpty());
        assertEquals(created.getTitle(), courses.getCourseById(created.getId()).getTitle());
    }
}
