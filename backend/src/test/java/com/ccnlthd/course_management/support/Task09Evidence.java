package com.ccnlthd.course_management.support;

import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public final class Task09Evidence {
    public static final JsonMapper JSON = JsonMapper.builder().build();

    private final Task09Environment runtime;

    public Task09Evidence(Task09Environment runtime) {
        this.runtime = runtime;
    }

    public Map<String, Object> environment() {
        Map<String, Object> result = new LinkedHashMap<>(runtime.metadata());
        result.put("database", "MySQL");
        result.put("database_user", runtime.dbUser());
        result.put("redis_host", runtime.redisHost());
        result.put("redis_port", Integer.toString(runtime.redisPort()));
        result.put("redis_database", 0);
        result.put("run_id", runtime.runId());
        result.put("source_sha", runtime.sourceSha());
        result.put("profile", "task09");
        result.put("execution", "SpringBootTest MOCK; real beans; MockMvc in-process; real MySQL and Redis; "
                + (runtime.automatic() ? "Testcontainers" : "external services"));
        result.put("rollback", "TransactionTemplate on test thread; real service proxies; explicit flush; forced exception");
        result.put("schema", "Flyway classpath:db/migration; Hibernate validate; SQL init never");
        result.put("java_version", System.getProperty("java.version"));
        result.put("java_runtime_version", System.getProperty("java.runtime.version"));
        result.put("java_vendor", System.getProperty("java.vendor"));
        result.put("java_home", System.getProperty("java.home"));
        result.put("test_jvm_pid", ProcessHandle.current().pid());
        return result;
    }

    public static Map<String, Object> redisState(StringRedisTemplate redis, Long courseId) {
        String key = "courseDetails::" + courseId;
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("key", key);
        state.put("exists", Boolean.TRUE.equals(redis.hasKey(key)));
        state.put("ttl_seconds", redis.getExpire(key, TimeUnit.SECONDS));
        state.put("pttl_ms", redis.getExpire(key, TimeUnit.MILLISECONDS));
        String value = redis.opsForValue().get(key);
        state.put("raw_json", value);
        state.put("json", value == null ? null : JSON.readTree(value));
        return state;
    }

    private Connection independentConnection() throws SQLException {
        Connection connection = DriverManager.getConnection(runtime.jdbcUrl(),
                runtime.dbUser(), runtime.dbPassword());
        try {
            if (!"MySQL".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName())) {
                throw new IllegalStateException("Task 9 requires a real MySQL server");
            }
            if (!"task09_cache".equals(connection.getCatalog())) {
                throw new IllegalStateException("Task 9 requires the dedicated task09_cache database");
            }
            connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            connection.setAutoCommit(true);
            return connection;
        } catch (SQLException | RuntimeException exception) {
            connection.close();
            throw exception;
        }
    }

    public Map<String, Object> committedSnapshot(Long categoryId, List<Long> courseIds) {
        try (Connection connection = independentConnection()) {
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("scope", "committed rows; independent JDBC connection; READ_COMMITTED; autoCommit=true");
            snapshot.put("database_product", connection.getMetaData().getDatabaseProductName());
            snapshot.put("jdbc_url", connection.getMetaData().getURL());
            snapshot.put("auto_commit", connection.getAutoCommit());
            try (var statement = connection.createStatement();
                 var rows = statement.executeQuery("SELECT VERSION(), DATABASE(), CONNECTION_ID(), @@hostname")) {
                rows.next();
                snapshot.put("server_version", rows.getString(1));
                snapshot.put("catalog", rows.getString(2));
                snapshot.put("connection_id", rows.getLong(3));
                snapshot.put("server_hostname", rows.getString(4));
            }
            snapshot.put("category", row(connection,
                    "SELECT id, name, description FROM categories WHERE id=?", categoryId));
            List<Map<String, Object>> courses = new ArrayList<>();
            for (Long id : courseIds) {
                Map<String, Object> course = row(connection,
                        "SELECT id, category_id, title, description, price, level, status FROM courses WHERE id=?", id);
                if (course == null) {
                    course = new LinkedHashMap<>();
                    course.put("id", id);
                    course.put("exists", false);
                } else {
                    course.put("exists", true);
                }
                courses.add(course);
            }
            snapshot.put("courses", courses);
            return snapshot;
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot capture committed MySQL rows", exception);
        }
    }

    private static Map<String, Object> row(Connection connection, String sql, Long id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) return null;
                Map<String, Object> result = new LinkedHashMap<>();
                var metadata = rows.getMetaData();
                for (int i = 1; i <= metadata.getColumnCount(); i++) {
                    result.put(metadata.getColumnLabel(i), rows.getObject(i));
                }
                return result;
            }
        }
    }

    private String scalar(String sql, Long id) {
        try (Connection connection = independentConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? rows.getString(1) : null;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot read committed MySQL value", exception);
        }
    }

    public String committedCategoryName(Long id) {
        return scalar("SELECT name FROM categories WHERE id=?", id);
    }

    public String committedCourseTitle(Long id) {
        return scalar("SELECT title FROM courses WHERE id=?", id);
    }

    private static void writeJson(Path file, Object value) throws IOException {
        Files.writeString(file, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(value));
    }

    public void write(String scenario, int configuredTtlSeconds, List<Map<String, Object>> steps)
            throws IOException {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("scenario", scenario);
        evidence.put("captured_at", Instant.now().toString());
        evidence.put("environment", environment());
        evidence.put("cache_name", "courseDetails");
        evidence.put("key_format", "courseDetails::<courseId>");
        evidence.put("dto", "CourseResponse with categoryId and categoryName");
        evidence.put("value_serializer", "JacksonJsonRedisSerializer<CourseResponse>");
        evidence.put("configured_ttl_seconds", configuredTtlSeconds);
        evidence.put("query_definition", "Hibernate statements for the action only; snapshot queries excluded. A JOIN can touch both tables.");
        evidence.put("steps", steps);
        Path directory = runtime.evidenceDirectory();
        Files.createDirectories(directory);
        writeJson(directory.resolve(scenario + ".json"), evidence);
        Path raw = directory.resolve(scenario);
        Files.createDirectories(raw);
        for (int i = 0; i < steps.size(); i++) {
            Map<String, Object> step = steps.get(i);
            String stage = step.get("stage").toString().replaceAll("[^a-zA-Z0-9_-]", "_");
            String prefix = String.format("%02d-%s", i + 1, stage);
            writeJson(raw.resolve(prefix + "-step.json"), step);
            writeJson(raw.resolve(prefix + "-redis.json"), step.get("redis"));
            writeJson(raw.resolve(prefix + "-committed-db.json"), step.get("committed_database"));
            if (step.get("response") != null) {
                writeJson(raw.resolve(prefix + "-api.json"), step.get("response"));
            }
            writeJson(raw.resolve(prefix + "-action-sql.json"), step.get("sql"));
        }
        System.out.printf("T9_EVIDENCE scenario=%s TTL=%ds file=%s%n", scenario,
                configuredTtlSeconds, directory.resolve(scenario + ".json").toAbsolutePath());
    }
}
