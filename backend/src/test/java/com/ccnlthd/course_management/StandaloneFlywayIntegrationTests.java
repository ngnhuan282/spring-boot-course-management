package com.ccnlthd.course_management;

import com.ccnlthd.course_management.entity.Course;
import com.ccnlthd.course_management.repository.CourseRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class StandaloneFlywayIntegrationTests {
    private static final String PASSWORD = "standalone_flyway_test_only";

    @Container
    static final GenericContainer<?> MYSQL = new GenericContainer<>(DockerImageName.parse("mysql:8.4"))
            .withEnv("MYSQL_ROOT_PASSWORD", PASSWORD)
            .withEnv("MYSQL_ROOT_HOST", "%")
            .withExposedPorts(3306)
            .waitingFor(Wait.forSuccessfulCommand("MYSQL_PWD=" + PASSWORD
                    + " mysql --protocol=TCP --host=127.0.0.1 --user=root --execute=\"SELECT 1\"")
                    .withStartupTimeout(Duration.ofMinutes(5)));

    @Test
    void freshDatabaseMigratesBeforeJpaAndRestartKeepsEditedAndDeletedData() throws Exception {
        String database = createDatabase();
        try (var context = start(database, false)) {
            assertRuntimePolicy(context);
            assertEquals(3, number(database, "SELECT COUNT(*) FROM flyway_schema_history WHERE success=1"));
            assertEquals(3, number(database, "SELECT COUNT(*) FROM courses"));
            assertEquals(0, number(database, "SELECT COUNT(*) FROM information_schema.COLUMNS "
                    + "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='courses' AND COLUMN_NAME='category_id'"));
            execute(database, "UPDATE courses SET title='Edited after migration' WHERE id=1",
                    "DELETE FROM courses WHERE id=3");
        }
        List<String> courses = rows(database, "SELECT * FROM courses ORDER BY id");
        List<String> history = rows(database, "SELECT * FROM flyway_schema_history ORDER BY installed_rank");
        try (var context = start(database, false)) {
            assertRuntimePolicy(context);
            assertEquals(courses, rows(database, "SELECT * FROM courses ORDER BY id"));
            assertEquals(history, rows(database, "SELECT * FROM flyway_schema_history ORDER BY installed_rank"));
            assertEquals(0, number(database, "SELECT COUNT(*) FROM courses WHERE id=3"));
            assertEquals("Edited after migration", context.getBean(CourseRepository.class)
                    .findById(1L).orElseThrow().getTitle());
        }
        System.out.println("STANDALONE_FLYWAY fresh=PASS migrations=3 ddl=validate restart=UNCHANGED seed=ONCE");
    }

    @Test
    void legacyDatabaseRequiresExplicitBaselineAndRetainsRowsWhileDetachingRelationships() throws Exception {
        String database = createDatabase();
        execute(database,
                "CREATE TABLE categories (id BIGINT PRIMARY KEY, name VARCHAR(100) NOT NULL)",
                "CREATE TABLE students (id BIGINT PRIMARY KEY, full_name VARCHAR(150) NOT NULL)",
                "CREATE TABLE courses (id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, category_id BIGINT NOT NULL, "
                        + "title VARCHAR(150) NOT NULL, description TEXT, price DECIMAL(12,2) NOT NULL, "
                        + "level VARCHAR(50), status VARCHAR(50) NOT NULL, created_at DATETIME(6) NOT NULL, "
                        + "updated_at DATETIME(6), CONSTRAINT legacy_course_category FOREIGN KEY (category_id) REFERENCES categories(id))",
                "CREATE TABLE enrollments (id BIGINT PRIMARY KEY, course_id BIGINT NOT NULL, "
                        + "CONSTRAINT legacy_enrollment_course FOREIGN KEY (course_id) REFERENCES courses(id))",
                "INSERT INTO categories VALUES (7, 'Legacy category')",
                "INSERT INTO students VALUES (8, 'Legacy student')",
                "INSERT INTO courses VALUES (1,7,'Existing course','Keep description',123.45,'BEGINNER','DRAFT',NOW(6),NOW(6))",
                "INSERT INTO enrollments VALUES (9,1)");
        List<String> oldCourse = rows(database, "SELECT * FROM courses WHERE id=1");
        List<String> oldCategory = rows(database, "SELECT * FROM categories");
        List<String> oldStudent = rows(database, "SELECT * FROM students");
        List<String> oldEnrollment = rows(database, "SELECT * FROM enrollments");

        RuntimeException rejected = assertThrows(RuntimeException.class, () -> {
            try (var ignored = start(database, false)) {
                fail("A non-empty schema without Flyway history must require explicit adoption");
            }
        });
        assertTrue(causes(rejected).contains("non-empty schema"), causes(rejected));
        assertEquals(oldCourse, rows(database, "SELECT * FROM courses WHERE id=1"));

        try (var context = start(database, true)) {
            assertEquals("3", context.getBean(Flyway.class).info().current().getVersion().getVersion());
            assertEquals(3, number(database, "SELECT COUNT(*) FROM flyway_schema_history WHERE type='SQL' AND success=1"));
            assertEquals(1, number(database, "SELECT COUNT(*) FROM flyway_schema_history WHERE type='BASELINE' AND version='0'"));
            assertEquals(oldCourse, rows(database, "SELECT * FROM courses WHERE id=1"));
            assertEquals(oldCategory, rows(database, "SELECT * FROM categories"));
            assertEquals(oldStudent, rows(database, "SELECT * FROM students"));
            assertEquals(oldEnrollment, rows(database, "SELECT * FROM enrollments"));
            assertEquals(0, number(database, "SELECT COUNT(*) FROM information_schema.KEY_COLUMN_USAGE "
                    + "WHERE TABLE_SCHEMA=DATABASE() AND ((TABLE_NAME='courses' AND COLUMN_NAME='category_id' "
                    + "AND REFERENCED_TABLE_NAME='categories') OR (TABLE_NAME='enrollments' AND COLUMN_NAME='course_id' "
                    + "AND REFERENCED_TABLE_NAME='courses'))"));
            assertEquals(List.of("YES"), rows(database, "SELECT IS_NULLABLE FROM information_schema.COLUMNS "
                    + "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='courses' AND COLUMN_NAME='category_id'"));

            Course independent = new Course();
            independent.setTitle("Course without Category");
            independent.setPrice(new BigDecimal("99.00"));
            independent.setStatus("DRAFT");
            var repository = context.getBean(CourseRepository.class);
            assertNotNull(repository.saveAndFlush(independent).getId());
            repository.deleteById(1L);
            assertEquals(oldEnrollment, rows(database, "SELECT * FROM enrollments"));
        }
        List<String> afterMigration = rows(database, "SELECT * FROM courses ORDER BY id");
        try (var context = start(database, false)) {
            assertRuntimePolicy(context);
            assertEquals(afterMigration, rows(database, "SELECT * FROM courses ORDER BY id"));
            assertEquals(oldEnrollment, rows(database, "SELECT * FROM enrollments"));
        }
        System.out.println("STANDALONE_FLYWAY legacy=PASS default=REFUSED baseline=0 rows=PRESERVED restart=UNCHANGED");
    }

    private ConfigurableApplicationContext start(String database, boolean adoptLegacy) {
        List<String> arguments = new ArrayList<>(List.of(
                "--spring.datasource.url=" + url(database), "--spring.datasource.username=root",
                "--spring.datasource.password=" + PASSWORD, "--spring.jpa.show-sql=false",
                "--spring.output.ansi.enabled=never"));
        if (adoptLegacy) {
            arguments.add("--spring.flyway.baseline-on-migrate=true");
            arguments.add("--spring.flyway.baseline-version=0");
        }
        return new SpringApplicationBuilder(CourseManagementApplication.class)
                .web(WebApplicationType.NONE).registerShutdownHook(false)
                .run(arguments.toArray(String[]::new));
    }

    private void assertRuntimePolicy(ConfigurableApplicationContext context) {
        var environment = context.getEnvironment();
        assertEquals(Boolean.TRUE, environment.getProperty("spring.flyway.enabled", Boolean.class));
        assertEquals(Boolean.FALSE, environment.getProperty("spring.flyway.baseline-on-migrate", Boolean.class));
        assertEquals(Boolean.TRUE, environment.getProperty("spring.flyway.validate-on-migrate", Boolean.class));
        assertEquals("validate", environment.getProperty("spring.jpa.hibernate.ddl-auto"));
        assertEquals(Boolean.FALSE, environment.getProperty("spring.jpa.defer-datasource-initialization", Boolean.class));
        assertEquals("never", environment.getProperty("spring.sql.init.mode"));
        assertEquals("3", context.getBean(Flyway.class).info().current().getVersion().getVersion());
        assertEquals(0, context.getBean(Flyway.class).info().pending().length);
    }

    private String createDatabase() throws SQLException {
        String name = "standalone_" + UUID.randomUUID().toString().replace("-", "");
        execute("mysql", "CREATE DATABASE `" + name + "`");
        return name;
    }

    private String url(String database) {
        return "jdbc:mysql://" + MYSQL.getHost() + ":" + MYSQL.getMappedPort(3306) + "/" + database
                + "?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=UTC";
    }

    private Connection connection(String database) throws SQLException {
        return DriverManager.getConnection(url(database), "root", PASSWORD);
    }

    private void execute(String database, String... statements) throws SQLException {
        try (var connection = connection(database); var statement = connection.createStatement()) {
            for (String sql : statements) statement.execute(sql);
        }
    }

    private List<String> rows(String database, String sql) throws SQLException {
        try (var connection = connection(database); var statement = connection.createStatement();
             var result = statement.executeQuery(sql)) {
            List<String> rows = new ArrayList<>();
            while (result.next()) {
                List<String> columns = new ArrayList<>();
                for (int i = 1; i <= result.getMetaData().getColumnCount(); i++) columns.add(result.getString(i));
                rows.add(String.join("|", columns.stream().map(value -> value == null ? "<NULL>" : value).toList()));
            }
            return rows;
        }
    }

    private int number(String database, String sql) throws SQLException {
        return Integer.parseInt(rows(database, sql).getFirst());
    }

    private String causes(Throwable failure) {
        StringBuilder messages = new StringBuilder();
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) messages.append(cause.getMessage()).append('\n');
        return messages.toString();
    }
}
