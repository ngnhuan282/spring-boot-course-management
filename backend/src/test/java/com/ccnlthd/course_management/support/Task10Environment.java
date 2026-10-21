package com.ccnlthd.course_management.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Task 10 uses its explicit evidence environment, or owns fresh test containers. */
public final class Task10Environment implements AutoCloseable {
    private static final String DB = "task10_concurrency";
    private static final String USER = "task10_user";
    private static final String PASSWORD = "task10_local_password";

    private record Settings(String jdbcUrl, String dbUser, String dbPassword,
                            String redisHost, int redisPort, boolean guardEnabled,
                            String runId, String sourceSha, Path evidenceDirectory) {}

    private final Settings settings;
    private final GenericContainer<?> mysql;
    private final GenericContainer<?> redis;
    private final Map<String, Object> source;
    private boolean closed;

    private Task10Environment(Settings settings, GenericContainer<?> mysql,
                              GenericContainer<?> redis, Map<String, Object> source) {
        this.settings = settings;
        this.mysql = mysql;
        this.redis = redis;
        this.source = source;
    }

    public static Task10Environment create() {
        Map<String, String> env = System.getenv();
        Map<String, Object> source = sourceSnapshot();
        if (env.keySet().stream().anyMatch(name -> name.startsWith("T10_"))) {
            String url = required(env, "T10_JDBC_URL");
            if (!url.startsWith("jdbc:mysql://127.0.0.1:") || !url.contains("/" + DB + "?")) {
                throw new IllegalStateException("Use the dedicated local task10_concurrency database");
            }
            String guard = required(env, "T10_GUARD_ENABLED");
            if (!guard.equals("true") && !guard.equals("false")) {
                throw new IllegalStateException("T10_GUARD_ENABLED must be true or false");
            }
            Settings settings = new Settings(url, required(env, "T10_DB_USER"),
                    required(env, "T10_DB_PASSWORD"), required(env, "T10_REDIS_HOST"),
                    port(required(env, "T10_REDIS_PORT")), Boolean.parseBoolean(guard),
                    required(env, "T10_RUN_ID"), required(env, "T10_SOURCE_SHA"),
                    Path.of(required(env, "T10_EVIDENCE_DIR")).toAbsolutePath().normalize());
            return new Task10Environment(settings, null, null, source);
        }

        String runId = "auto-" + DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS")
                .withZone(ZoneOffset.UTC).format(Instant.now()) + "-"
                + UUID.randomUUID().toString().substring(0, 8);
        Path evidence = Path.of("target", "task10-evidence", runId, "scenarios")
                .toAbsolutePath().normalize();
        GenericContainer<?> mysql = new GenericContainer<>(DockerImageName.parse("mysql:8.4"))
                .withExposedPorts(3306)
                .withEnv("MYSQL_DATABASE", DB)
                .withEnv("MYSQL_USER", USER)
                .withEnv("MYSQL_PASSWORD", PASSWORD)
                .withEnv("MYSQL_ROOT_PASSWORD", "task10_local_root_password")
                .waitingFor(Wait.forSuccessfulCommand("MYSQL_PWD=" + PASSWORD
                        + " mysql --protocol=TCP --host=127.0.0.1 --user=" + USER
                        + " --database=" + DB + " --execute=\"SELECT 1\"")
                        .withStartupTimeout(Duration.ofMinutes(5)));
        GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
                .withExposedPorts(6379)
                .waitingFor(Wait.forSuccessfulCommand("redis-cli ping")
                        .withStartupTimeout(Duration.ofMinutes(1)));
        try {
            Files.createDirectories(evidence);
            mysql.start();
            redis.start();
            String url = "jdbc:mysql://" + address(mysql.getHost()) + ":" + mysql.getMappedPort(3306)
                    + "/" + DB + "?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=UTC";
            Settings settings = new Settings(url, USER, PASSWORD, redis.getHost(),
                    redis.getMappedPort(6379), true, runId,
                    source.get("git_head").toString(), evidence);
            Task10Environment result = new Task10Environment(settings, mysql, redis, source);
            System.out.println("T10_ENVIRONMENT " + Task10Harness.JSON.writeValueAsString(result.metadata()));
            return result;
        } catch (Exception | LinkageError failure) {
            diagnostics(mysql, "MySQL");
            diagnostics(redis, "Redis");
            stop(redis);
            stop(mysql);
            throw new IllegalStateException("Cannot start isolated Task 10 MySQL/Redis", failure);
        }
    }

    public void register(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", this::jdbcUrl);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.datasource.username", this::dbUser);
        registry.add("spring.datasource.password", this::dbPassword);
        registry.add("spring.data.redis.host", settings::redisHost);
        registry.add("spring.data.redis.port", settings::redisPort);
        registry.add("spring.data.redis.database", () -> 0);
        registry.add("spring.data.redis.url", () -> "redis://" + address(settings.redisHost())
                + ":" + settings.redisPort() + "/0");
        registry.add("spring.data.redis.ssl.enabled", () -> false);
    }

    public String jdbcUrl() { return settings.jdbcUrl(); }
    public String dbUser() { return settings.dbUser(); }
    public String dbPassword() { return settings.dbPassword(); }
    public Path evidenceDirectory() { return settings.evidenceDirectory(); }
    public String runId() { return settings.runId(); }
    public String sourceSha() { return settings.sourceSha(); }
    public boolean automatic() { return mysql != null; }
    public boolean guardEnabled() { return settings.guardEnabled(); }

    public Map<String, Object> metadata() {
        Map<String, Object> result = new LinkedHashMap<>(source);
        result.put("infrastructure_mode", automatic() ? "testcontainers" : "external");
        result.put("guard_enabled", settings.guardEnabled());
        result.put("jdbc_url", jdbcUrl().replaceAll("(?i)(password=)[^&]*", "$1<redacted>"));
        result.put("database_user", dbUser());
        result.put("redis_host", settings.redisHost());
        result.put("redis_port", settings.redisPort());
        result.put("redis_database", 0);
        result.put("run_id", runId());
        result.put("source_sha", sourceSha());
        result.put("evidence_directory", evidenceDirectory().toString());
        if (automatic()) {
            result.put("mysql_image", "mysql:8.4");
            result.put("redis_image", "redis:7.4-alpine");
            result.put("mysql_container_id", mysql.getContainerId());
            result.put("redis_container_id", redis.getContainerId());
        }
        return result;
    }

    @Override
    public synchronized void close() {
        if (closed || !automatic()) return;
        closed = true;
        try {
            Path logs = evidenceDirectory().getParent().resolve("infrastructure");
            Files.createDirectories(logs);
            Files.writeString(logs.resolve("mysql.log"), mysql.getLogs(), StandardCharsets.UTF_8);
            Files.writeString(logs.resolve("redis.log"), redis.getLogs(), StandardCharsets.UTF_8);
        } catch (Exception failure) {
            System.err.println("Cannot save Task 10 container logs: " + failure);
        } finally {
            stop(redis);
            stop(mysql);
        }
    }

    private static String required(Map<String, String> env, String name) {
        String value = env.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing " + name
                    + "; complete the external T10_* configuration or remove all T10_* variables"
                    + " to use isolated Testcontainers");
        }
        return value;
    }

    private static int port(String text) {
        try {
            int value = Integer.parseInt(text);
            if (value >= 1 && value <= 65535) return value;
        } catch (NumberFormatException ignored) {
            // Report the configuration name rather than a generic conversion failure.
        }
        throw new IllegalStateException("T10_REDIS_PORT must be between 1 and 65535");
    }

    private static String address(String host) {
        return host.contains(":") && !host.startsWith("[") ? "[" + host + "]" : host;
    }

    private static Map<String, Object> sourceSnapshot() {
        Map<String, Object> result = new LinkedHashMap<>();
        String head = git("rev-parse", "HEAD");
        String status = git("status", "--porcelain");
        result.put("git_capture_at_utc", Instant.now().toString());
        result.put("git_head", head == null || head.isBlank() ? "unknown" : head.strip());
        result.put("git_status", status == null ? "unknown" : status);
        result.put("git_working_tree", status == null ? "unknown" : status.isBlank() ? "clean" : "dirty");
        result.put("git_capture_scope", "environment creation only; not a before/after source snapshot");
        return result;
    }

    private static String git(String... arguments) {
        List<String> command = new ArrayList<>(List.of("git"));
        command.addAll(List.of(arguments));
        Process process = null;
        try {
            process = new ProcessBuilder(command).redirectErrorStream(true).start();
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return null;
            }
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return process.exitValue() == 0 ? output : null;
        } catch (IOException failure) {
            return null;
        } catch (InterruptedException failure) {
            if (process != null) process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while capturing Task 10 Git state", failure);
        }
    }

    private static void diagnostics(GenericContainer<?> container, String name) {
        if (container.getContainerId() == null) return;
        try {
            String logs = container.getLogs();
            System.err.println("Task 10 " + name + " startup log:\n"
                    + logs.substring(Math.max(0, logs.length() - 4000)));
        } catch (Exception failure) {
            System.err.println("Cannot read Task 10 " + name + " startup log: " + failure);
        }
    }

    private static void stop(GenericContainer<?> container) {
        try {
            container.stop();
        } catch (Exception failure) {
            System.err.println("Cannot stop owned Task 10 container: " + failure);
        }
    }
}
