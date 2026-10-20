package com.ccnlthd.course_management.support;

import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.springframework.aop.Advisor;
import org.springframework.aop.Pointcut;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public final class Task10Harness implements StatementInspector {
    public static final JsonMapper JSON = JsonMapper.builder().build();
    public static final int WAIT_SECONDS = 30;
    public record Pause(String method, CountDownLatch ready, CountDownLatch release) {}
    private record Installed(Advised proxy, Advisor outer, Advisor inner) {}

    private final AtomicLong sequence = new AtomicLong();
    private final ThreadLocal<String> actor = ThreadLocal.withInitial(() -> "MAIN");
    private final List<Map<String, Object>> events = new CopyOnWriteArrayList<>();
    private final List<Map<String, Object>> steps = new CopyOnWriteArrayList<>();
    private final List<Map<String, Object>> exchanges = new CopyOnWriteArrayList<>();
    private final Map<String, Pause> pauses = new ConcurrentHashMap<>();
    private final Map<String, CountDownLatch> entries = new ConcurrentHashMap<>();
    private final Map<String, Thread> workers = new ConcurrentHashMap<>();
    private final List<Installed> installed = new ArrayList<>();
    private String scenario;

    public static Map<String, Object> row(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            result.put(pairs[i].toString(), pairs[i + 1]);
        }
        return result;
    }

    public static String env(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("Missing " + name);
        return value;
    }

    public void reset(String name) {
        releaseAll();
        scenario = name;
        events.clear(); steps.clear(); exchanges.clear(); pauses.clear();
        entries.clear(); workers.clear(); sequence.set(0);
        actor.set("MAIN");
    }

    public void actor(String name) {
        actor.set(name);
        workers.put(name, Thread.currentThread());
    }

    public void clearActor() {
        actor.remove();
    }

    public Thread worker(String name) {
        Thread result = workers.get(name);
        if (result == null) throw new IllegalStateException("No worker " + name);
        return result;
    }

    public synchronized void event(String kind, Object details) {
        Map<String, Object> value = row("sequence", sequence.incrementAndGet(),
                "at_utc", Instant.now().toString(), "nano_time", System.nanoTime(),
                "actor", actor.get(), "thread_id", Thread.currentThread().threadId(),
                "thread_name", Thread.currentThread().getName(), "kind", kind,
                "details", details);
        events.add(value);
        System.out.println("T10_EVENT " + JSON.writeValueAsString(value));
    }

    @Override
    public String inspect(String sql) {
        event("HIBERNATE_SQL", sql);
        return sql;
    }

    public long sqlCount() {
        return events.stream().filter(e -> "HIBERNATE_SQL".equals(e.get("kind"))).count();
    }

    public Pause pause(String name, String method) {
        Pause pause = new Pause(method, new CountDownLatch(1), new CountDownLatch(1));
        pauses.put(name, pause);
        return pause;
    }

    public void awaitReady(Pause pause) throws InterruptedException {
        if (!pause.ready().await(WAIT_SECONDS, TimeUnit.SECONDS)) {
            throw new AssertionError("TARGET_RETURN latch timed out: " + pause.method());
        }
    }

    public void awaitEntry(String name) throws InterruptedException {
        if (!entries.computeIfAbsent(name, k -> new CountDownLatch(1))
                .await(WAIT_SECONDS, TimeUnit.SECONDS)) {
            throw new AssertionError("PROXY_ENTER latch timed out: " + name);
        }
    }

    public void release(Pause pause) {
        pause.release().countDown();
    }

    public void releaseAll() {
        pauses.values().forEach(p -> p.release().countDown());
    }

    public void install(Object service, JdbcTemplate jdbc) {
        if (!(service instanceof Advised advised) || advised.isFrozen()) {
            throw new IllegalStateException("A mutable Spring service proxy is required");
        }
        // Insert outside all production advice: records a competing request
        // reaching the service even when it subsequently queues at the gate.
        Advisor outer = new DefaultPointcutAdvisor(Pointcut.TRUE, (MethodInterceptor) invocation -> {
            String method = invocation.getMethod().getName();
            String name = actor.get();
            event("PROXY_ENTER", method);
            entries.computeIfAbsent(name, k -> new CountDownLatch(1)).countDown();
            Object value = invocation.proceed();
            event("PROXY_RETURN", method);
            return value;
        });
        // Append inside cache + transaction advice: the DTO is already built,
        // but transaction completion and cache publication have not occurred.
        Advisor inner = new DefaultPointcutAdvisor(Pointcut.TRUE, (MethodInterceptor) invocation -> {
            String method = invocation.getMethod().getName();
            String name = actor.get();
            boolean active = TransactionSynchronizationManager.isActualTransactionActive();
            event("TARGET_ENTER", row("method", method, "transaction_active", active,
                    "mysql_connection", active ? jdbc.queryForMap(
                            "SELECT CONNECTION_ID() AS connection_id, "
                                    + "@@transaction_isolation AS isolation_level") : null));
            if (active) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override public void beforeCommit(boolean readOnly) {
                        event("TX_BEFORE_COMMIT", row("method", method, "read_only", readOnly));
                    }
                    @Override public void afterCommit() {
                        event("TX_AFTER_COMMIT_CALLBACK", method);
                    }
                    @Override public void afterCompletion(int status) {
                        event("TX_AFTER_COMPLETION", row("method", method, "status", status));
                    }
                });
            }
            Object value = invocation.proceed();
            event("TARGET_RETURN", row("method", method, "dto",
                    value == null ? null : JSON.readTree(JSON.writeValueAsString(value))));
            Pause pause = pauses.get(name);
            if (pause != null && pause.method().equals(method)) {
                event("LATCH_READY", method);
                pause.ready().countDown();
                if (!pause.release().await(WAIT_SECONDS, TimeUnit.SECONDS)) {
                    throw new AssertionError("Release latch timed out: " + name);
                }
                event("LATCH_RELEASED", method);
            }
            return value;
        });
        advised.addAdvisor(0, outer);
        advised.addAdvisor(inner);
        installed.add(new Installed(advised, outer, inner));
    }

    public void uninstall() {
        installed.forEach(x -> {
            x.proxy().removeAdvisor(x.outer());
            x.proxy().removeAdvisor(x.inner());
        });
        installed.clear();
    }

    public void exchange(String method, String path, String request, int status, String body) {
        var exchange = row("at_utc", Instant.now().toString(), "actor", actor.get(),
                "method", method, "path", path, "request_body", request,
                "status", status, "response_body", body);
        exchanges.add(exchange);
        event("HTTP_RESPONSE", exchange);
    }

    public Map<String, Object> database(Long categoryId, List<Long> ids) {
        try (Connection c = DriverManager.getConnection(
                env("T10_JDBC_URL"), env("T10_DB_USER"), env("T10_DB_PASSWORD"))) {
            c.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            c.setAutoCommit(true);
            if (!"MySQL".equals(c.getMetaData().getDatabaseProductName())
                    || !"task10_concurrency".equals(c.getCatalog())) {
                throw new IllegalStateException("Expected the dedicated task10_concurrency MySQL DB");
            }
            Map<String, Object> result = row("scope", "independent JDBC; READ_COMMITTED; autocommit",
                    "product", c.getMetaData().getDatabaseProductName(),
                    "driver", c.getMetaData().getDriverVersion(),
                    "server", c.getMetaData().getDatabaseProductVersion());
            try (var s = c.createStatement(); var rs = s.executeQuery(
                    "SELECT VERSION(), DATABASE(), CONNECTION_ID(), @@transaction_isolation")) {
                rs.next();
                result.put("server_version", rs.getString(1));
                result.put("database", rs.getString(2));
                result.put("connection_id", rs.getLong(3));
                result.put("isolation", rs.getString(4));
            }
            try (var p = c.prepareStatement("SELECT id,name,description FROM categories WHERE id=?")) {
                p.setLong(1, categoryId);
                try (var rs = p.executeQuery()) {
                    if (!rs.next()) throw new IllegalStateException("Missing fixture Category");
                    result.put("category", row("id", rs.getLong("id"), "name", rs.getString("name"),
                            "description", rs.getString("description")));
                }
            }
            List<Map<String, Object>> courses = new ArrayList<>();
            for (Long id : ids) {
                try (var p = c.prepareStatement("SELECT c.id,c.category_id,g.name AS category_name,"
                        + "c.title,c.description,c.price,c.level,c.status FROM courses c "
                        + "JOIN categories g ON g.id=c.category_id WHERE c.id=?")) {
                    p.setLong(1, id);
                    try (var rs = p.executeQuery()) {
                        if (!rs.next()) throw new IllegalStateException("Missing fixture Course " + id);
                        courses.add(row("id", rs.getLong("id"), "category_id", rs.getLong("category_id"),
                                "category_name", rs.getString("category_name"), "title", rs.getString("title"),
                                "description", rs.getString("description"), "price", rs.getBigDecimal("price"),
                                "level", rs.getString("level"), "status", rs.getString("status")));
                    }
                }
            }
            result.put("courses", courses);
            return result;
        } catch (Exception e) {
            throw new IllegalStateException("Cannot capture committed MySQL snapshot", e);
        }
    }

    public Map<String, Object> redis(StringRedisTemplate redis, Long id) {
        String key = "courseDetails::" + id;
        String value = redis.opsForValue().get(key);
        return row("key", key, "raw_json", value,
                "json", value == null ? null : JSON.readTree(value),
                "ttl_seconds", redis.getExpire(key, TimeUnit.SECONDS),
                "pttl_ms", redis.getExpire(key, TimeUnit.MILLISECONDS));
    }

    public Map<String, Object> capture(String stage, Long categoryId,
                                       List<Long> ids, StringRedisTemplate redis) {
        Map<String, Object> step = row("stage", stage, "at_utc", Instant.now().toString(),
                "database", database(categoryId, ids),
                "redis", ids.stream().map(id -> redis(redis, id)).toList());
        steps.add(step);
        event("SNAPSHOT", stage);
        return step;
    }

    public void write(boolean passed, boolean guardEnabled) throws IOException {
        Path root = Path.of(env("T10_EVIDENCE_DIR")).resolve(scenario);
        Files.createDirectories(root);
        var document = row("scenario", scenario, "passed", passed,
                "run_id", env("T10_RUN_ID"), "source_sha", env("T10_SOURCE_SHA"),
                "guard_enabled", guardEnabled, "profile", "task10",
                "policy", "serialized writes; last applied committed PUT wins; one JVM",
                "execution", "SpringBootTest MOCK; MockMvc; real MySQL and Redis; host test JVM",
                "java_version", System.getProperty("java.version"),
                "java_runtime_version", System.getProperty("java.runtime.version"),
                "java_vendor", System.getProperty("java.vendor"),
                "java_home", System.getProperty("java.home"),
                "test_jvm_pid", ProcessHandle.current().pid(), "configured_ttl_seconds", 600,
                "events", List.copyOf(events), "steps", List.copyOf(steps),
                "http_exchanges", List.copyOf(exchanges));
        json(root.resolve("scenario.json"), document);
        json(root.resolve("timeline.json"), List.copyOf(events));
        for (int i = 0; i < steps.size(); i++) {
            json(root.resolve(String.format("%02d-%s.json", i + 1, steps.get(i).get("stage"))),
                    steps.get(i));
        }
        Path http = root.resolve("http");
        Files.createDirectories(http);
        for (int i = 0; i < exchanges.size(); i++) {
            Map<String, Object> exchange = exchanges.get(i);
            String base = String.format("%02d-%s", i + 1, exchange.get("actor"));
            json(http.resolve(base + ".metadata.json"), exchange);
            Files.writeString(http.resolve(base + ".response.json"),
                    exchange.get("response_body").toString(), StandardCharsets.UTF_8);
            String request = exchange.get("request_body").toString();
            if (!request.isEmpty()) {
                Files.writeString(http.resolve(base + ".request.json"), request, StandardCharsets.UTF_8);
            }
        }
    }

    private static void json(Path path, Object value) throws IOException {
        Files.writeString(path, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(value),
                StandardCharsets.UTF_8);
    }
}
