package com.ccnlthd.course_management;

import com.ccnlthd.course_management.dto.request.EnrollmentRequest;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.FileSystemResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/** Loads real repository configuration; does not start a server, DataSource or Flyway migration. */
class FlywayRuntimeConfigurationTests {
    private static final Path MAIN = Path.of("src/main/resources").toAbsolutePath().normalize();
    private static final List<String> RUNTIME_KEYS = List.of(
            "spring.flyway.enabled", "spring.flyway.locations", "spring.flyway.baseline-on-migrate",
            "spring.flyway.validate-on-migrate", "spring.jpa.hibernate.ddl-auto",
            "spring.jpa.defer-datasource-initialization", "spring.sql.init.mode");

    @Test
    void defaultDevUsesFlywayAndOptsInToDemoData() {
        configuration(MAIN, "").run(context -> {
            assertNull(context.getStartupFailure());
            var env = context.getEnvironment();
            assertArrayEquals(new String[]{"dev"}, env.getDefaultProfiles());
            assertEquals(0, env.getActiveProfiles().length);
            assertRuntimeContract(env);
            assertEquals(List.of("classpath:db/migration", "classpath:db/demo"), locations(env));
            record("default-dev", env);
        });
    }

    @Test
    void explicitNonDemoProfileLoadsSchemaWithoutDemoData() {
        configuration(MAIN, "prod").run(context -> {
            assertNull(context.getStartupFailure());
            var env = context.getEnvironment();
            assertArrayEquals(new String[]{"prod"}, env.getActiveProfiles());
            assertRuntimeContract(env);
            assertEquals(List.of("classpath:db/migration"), locations(env));
            record("explicit-prod-no-demo", env);
        });
    }

    @Test
    void migrationLabKeepsItsOwnDatabaseAndSchemaOnlyLocations() {
        configuration(MAIN, "m11-migration").run(context -> {
            assertNull(context.getStartupFailure());
            var env = context.getEnvironment();
            assertRuntimeContract(env);
            assertEquals(List.of("classpath:db/migration"), locations(env));
            assertTrue(env.getRequiredProperty("spring.datasource.url").contains("course_management_m11_migration"));
            assertEquals("none", env.getRequiredProperty("spring.cache.type"));
            record("m11-migration", env);
        });
    }

    @Test
    void isolatedH2TestsKeepTheirSeparateInitializationStrategy() {
        configuration(Path.of("src/test/resources").toAbsolutePath().normalize(), "cv3-test").run(context -> {
            assertNull(context.getStartupFailure());
            var env = context.getEnvironment();
            assertEquals(Boolean.FALSE, env.getProperty("spring.flyway.enabled", Boolean.class));
            assertEquals("create-drop", env.getRequiredProperty("spring.jpa.hibernate.ddl-auto"));
            assertEquals(Boolean.TRUE, env.getProperty("spring.jpa.defer-datasource-initialization", Boolean.class));
            assertEquals("always", env.getRequiredProperty("spring.sql.init.mode"));
            assertTrue(env.getRequiredProperty("spring.datasource.url").startsWith("jdbc:h2:mem:"));
            record("isolated-h2-test-only", env);
        });
    }

    @Test
    void composeDeclaresTheSameRuntimeContractAndExplicitLocalDemo() throws IOException {
        Path file = Path.of("../docker-compose.backend.yml").toAbsolutePath().normalize();
        List<PropertySource<?>> sources = new YamlPropertySourceLoader().load("compose", new FileSystemResource(file));
        assertEquals(1, sources.size());
        PropertySource<?> yaml = sources.getFirst();
        String prefix = "services.backend.environment.";
        assertEquals("true", String.valueOf(yaml.getProperty(prefix + "SPRING_FLYWAY_ENABLED")));
        assertEquals("false", String.valueOf(yaml.getProperty(prefix + "SPRING_FLYWAY_BASELINE_ON_MIGRATE")));
        assertEquals("true", String.valueOf(yaml.getProperty(prefix + "SPRING_FLYWAY_VALIDATE_ON_MIGRATE")));
        assertEquals("validate", yaml.getProperty(prefix + "SPRING_JPA_HIBERNATE_DDL_AUTO"));
        assertEquals("false", String.valueOf(yaml.getProperty(prefix + "SPRING_JPA_DEFER_DATASOURCE_INITIALIZATION")));
        assertEquals("never", yaml.getProperty(prefix + "SPRING_SQL_INIT_MODE"));
        assertEquals("classpath:db/migration,classpath:db/demo", yaml.getProperty(prefix + "SPRING_FLYWAY_LOCATIONS"));
        assertEquals("service_healthy", yaml.getProperty("services.backend.depends_on.mysql.condition"));
        assertEquals("service_healthy", yaml.getProperty("services.backend.depends_on.redis.condition"));
        String volume = String.valueOf(yaml.getProperty("services.mysql.volumes[0]"));
        assertTrue(volume.endsWith(":/var/lib/mysql"));
        assertFalse(volume.startsWith("/") || volume.startsWith("."), "Use a named volume isolated by Compose project");
        assertNull(yaml.getProperty("volumes." + volume.split(":")[0] + ".external"));
        StringBuilder text = new StringBuilder("scope=parsed Compose source; no containers started\n");
        for (String key : List.of("SPRING_FLYWAY_ENABLED", "SPRING_FLYWAY_LOCATIONS", "SPRING_FLYWAY_BASELINE_ON_MIGRATE",
                "SPRING_FLYWAY_VALIDATE_ON_MIGRATE", "SPRING_JPA_HIBERNATE_DDL_AUTO",
                "SPRING_JPA_DEFER_DATASOURCE_INITIALIZATION", "SPRING_SQL_INIT_MODE")) {
            text.append(key).append('=').append(yaml.getProperty(prefix + key)).append('\n');
        }
        evidence("compose-policy", text.toString());
        System.out.println("CV3_COMPOSE_POLICY_PASS flyway=true ddl=validate sqlInit=never demo=explicit scope=source-only");
    }

    @Test
    void flywayAutoConfigurationAndMysqlSupportAreOnTheClasspath() throws Exception {
        assertNotNull(Class.forName("org.flywaydb.core.Flyway"));
        assertNotNull(Class.forName("org.flywaydb.database.mysql.MySQLDatabaseType"));
        assertNotNull(Class.forName("com.mysql.cj.jdbc.Driver"));
        List<String> candidates = new ArrayList<>();
        ImportCandidates.load(AutoConfiguration.class, getClass().getClassLoader()).forEach(candidates::add);
        assertTrue(candidates.stream().anyMatch(c -> c.endsWith(".FlywayAutoConfiguration")),
                "Spring Boot must discover Flyway auto-configuration, not only flyway-core");
        evidence("classpath", "scope=classpath discovery; no database connection\nFlywayAutoConfiguration=discoverable\n"
                + "Flyway=" + org.flywaydb.core.Flyway.class.getPackage().getImplementationVersion() + "\nmysqlSupport=present\n");
        System.out.println("CV3_CLASSPATH_PASS flywayAutoConfiguration=true mysqlSupport=true noDatabaseConnection=true");
    }

    @Test
    void schemaAndDemoMigrationsRemainSeparateAndPackaged() throws IOException {
        String schema = "db/migration/V1__create_course_management_schema.sql";
        String demo = "db/demo/V2__seed_demo_data.sql";
        for (String file : List.of(schema, demo)) {
            assertTrue(Files.isRegularFile(MAIN.resolve(file)));
            assertNotNull(getClass().getClassLoader().getResource(file), "Migration missing from runtime resources: " + file);
        }
        assertFalse(Files.exists(MAIN.resolve("db/migration/V2__seed_demo_data.sql")), "Demo must remain opt-in by location");
        evidence("migration-resources", "scope=source/classpath inspection, not executed SQL\nV1=" + schema + "\nV2=" + demo + "\n");
        System.out.println("CV3_MIGRATION_RESOURCES_PASS V1=schema V2=separate-demo packaged=true");
    }

    @Test
    void demoEnrollmentRowsSatisfyTheCurrentRequestValidationContract() throws IOException {
        String sql = Files.readString(MAIN.resolve("db/demo/V2__seed_demo_data.sql"), StandardCharsets.UTF_8);
        String enrollmentRows = sql.substring(sql.indexOf("INSERT INTO enrollments"));
        var rows = Pattern.compile("\\((\\d+),\\s*(\\d+),\\s*(\\d+),\\s*CURRENT_TIMESTAMP,\\s*'([^']+)',\\s*(?:NULL|CURRENT_TIMESTAMP)\\)")
                .matcher(enrollmentRows);
        Set<String> statuses = new java.util.HashSet<>();
        int count = 0;
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            while (rows.find()) {
                EnrollmentRequest request = new EnrollmentRequest();
                request.setStudentId(Long.parseLong(rows.group(2)));
                request.setCourseId(Long.parseLong(rows.group(3)));
                request.setStatus(rows.group(4));
                assertTrue(factory.getValidator().validate(request).isEmpty(), "Invalid demo Enrollment row: " + rows.group());
                statuses.add(request.getStatus());
                count++;
            }
        }
        assertTrue(count > 0, "Must actually inspect demo Enrollment rows");
        assertEquals(Set.of("ACTIVE", "COMPLETED"), statuses);
        evidence("demo-contract", "scope=validation of SQL source rows; no INSERT executed\nrowsChecked=" + count
                + "\nstatuses=" + new java.util.TreeSet<>(statuses) + "\n");
        System.out.printf("CV3_DEMO_CONTRACT_PASS rows=%d statuses=ACTIVE,COMPLETED%n", count);
    }

    private ApplicationContextRunner configuration(Path directory, String activeProfile) {
        assertTrue(Files.isRegularFile(directory.resolve("application.yml")));
        return new ApplicationContextRunner()
                .withPropertyValues("spring.config.location=" + directory.toUri(), "spring.profiles.active=" + activeProfile)
                .withInitializer(context -> {
                    // Test repository config under controlled profiles; never inherit the caller's DB/secret settings.
                    var sources = context.getEnvironment().getPropertySources();
                    sources.remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
                    sources.remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
                    new ConfigDataApplicationContextInitializer().initialize(context);
                });
    }

    private void assertRuntimeContract(ConfigurableEnvironment env) {
        assertAll("Only Flyway owns schema initialization",
                () -> assertEquals(Boolean.TRUE, env.getProperty("spring.flyway.enabled", Boolean.class)),
                () -> assertEquals(Boolean.FALSE, env.getProperty("spring.flyway.baseline-on-migrate", Boolean.class)),
                () -> assertEquals(Boolean.TRUE, env.getProperty("spring.flyway.validate-on-migrate", Boolean.class)),
                () -> assertEquals("validate", env.getRequiredProperty("spring.jpa.hibernate.ddl-auto")),
                () -> assertEquals(Boolean.FALSE, env.getProperty("spring.jpa.defer-datasource-initialization", Boolean.class)),
                () -> assertEquals("never", env.getRequiredProperty("spring.sql.init.mode")));
    }

    private List<String> locations(ConfigurableEnvironment env) {
        return Binder.get(env).bind("spring.flyway.locations", Bindable.listOf(String.class)).orElse(List.of());
    }

    private void record(String scenario, ConfigurableEnvironment env) throws IOException {
        StringBuilder text = new StringBuilder("scope=Spring ConfigData loading; controlled profile inputs; no database/server\n");
        text.append("scenario=").append(scenario).append("\nactiveProfiles=")
                .append(String.join(",", env.getActiveProfiles())).append("\ndefaultProfiles=")
                .append(String.join(",", env.getDefaultProfiles())).append('\n');
        for (String key : RUNTIME_KEYS) text.append(key).append('=').append(env.getProperty(key, "(not set)")).append('\n');
        evidence(scenario, text.toString());
        System.out.printf("CV3_CONFIG_PASS scenario=%s flyway=%s ddl=%s sqlInit=%s locations=%s%n", scenario,
                env.getProperty("spring.flyway.enabled"), env.getProperty("spring.jpa.hibernate.ddl-auto"),
                env.getProperty("spring.sql.init.mode"), env.getProperty("spring.flyway.locations", "(not set)"));
    }

    private void evidence(String name, String text) throws IOException {
        String directory = System.getProperty("cv3.evidenceDir");
        if (directory == null || directory.isBlank()) return;
        Path file = Path.of(directory).resolve("configuration").resolve(name + ".txt");
        Files.createDirectories(file.getParent());
        Files.writeString(file, text, StandardCharsets.UTF_8);
    }
}
