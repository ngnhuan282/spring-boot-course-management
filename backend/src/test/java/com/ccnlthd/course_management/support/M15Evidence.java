package com.ccnlthd.course_management.support;

import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public final class M15Evidence {
    public static final JsonMapper JSON = JsonMapper.builder().build();

    private M15Evidence() {
    }

    public static Map<String, Object> redisState(StringRedisTemplate redis, Long courseId) {
        String key = "courseDetails::" + courseId;
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("key", key);
        state.put("exists", Boolean.TRUE.equals(redis.hasKey(key)));
        state.put("ttl_ms", redis.getExpire(key, TimeUnit.MILLISECONDS));
        String value = redis.opsForValue().get(key);
        state.put("json", value == null ? null : JSON.readTree(value));
        return state;
    }

    public static void write(String scenario, int configuredTtlSeconds, List<Map<String, Object>> steps)
            throws IOException {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("scenario", scenario);
        evidence.put("captured_at", Instant.now().toString());
        evidence.put("environment", "H2 MySQL mode; real Redis Testcontainers; real services; MockMvc for HTTP steps");
        evidence.put("cache_name", "courseDetails");
        evidence.put("key_format", "courseDetails::<courseId>");
        evidence.put("dto", "CourseResponse with categoryId and categoryName");
        evidence.put("value_serializer", "JacksonJsonRedisSerializer<CourseResponse>");
        evidence.put("configured_ttl_seconds", configuredTtlSeconds);
        evidence.put("query_definition", "SELECTs touching courses/categories; a JOIN may count in both. DB snapshots excluded.");
        evidence.put("steps", steps);
        Path directory = Path.of(System.getProperty("m15.evidence.directory", "target/m15-evidence"));
        Files.createDirectories(directory);
        Path file = directory.resolve(scenario + ".json");
        Files.writeString(file, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(evidence));
        System.out.printf("M15 evidence: scenario=%s, configuredTTL=%ds, file=%s%n",
                scenario, configuredTtlSeconds, file.toAbsolutePath());
    }
}
