package com.ccnlthd.course_management.config;

import com.ccnlthd.course_management.dto.response.CourseResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.transaction.TransactionAwareCacheDecorator;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.ReactiveRedisConnection;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.connection.ReactiveStringCommands;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStringCommands;
import org.springframework.data.redis.connection.SetCondition;
import org.springframework.data.redis.core.types.Expiration;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CourseCacheWriteOrderingTests {
    @Test
    void putPublishesReadableResponseBeforeReturningEvenWhenReactiveSetIsDelayed() {
        try (RedisCommands redis = new RedisCommands()) {
            Cache cache = productionCache(redis.factory);
            cache.put(7L, response());

            assertNotNull(redis.values.get("courseDetails::7"),
                    "A completed cache put must have an acknowledged Redis SET; "
                            + "the default asynchronous writer returns before this happens");
            CourseResponse cached = cache.get(7L, CourseResponse.class);
            assertNotNull(cached);
            assertEquals(7L, cached.getId());
            assertEquals("Committed title", cached.getTitle());
            assertFalse(redis.reactiveAcknowledgement.isDone(),
                    "The test must not accidentally complete the delayed reactive SET");
        }
    }

    @Test
    void outerTransactionDefersPublicationUntilCommitAndFinishesItBeforeCallbacksReturn() {
        try (RedisCommands redis = new RedisCommands()) {
            Cache cache = productionCache(redis.factory);
            assertInstanceOf(TransactionAwareCacheDecorator.class, cache);
            TransactionSynchronizationManager.initSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(true);
            try {
                cache.put(7L, response());
                assertNull(redis.values.get("courseDetails::7"),
                        "An uncommitted response must not be published to Redis");
                TransactionSynchronizationUtils.triggerAfterCommit();

                assertNotNull(redis.values.get("courseDetails::7"),
                        "Commit callbacks must finish the Redis SET before the gate can unlock");
                TransactionSynchronizationUtils.triggerAfterCompletion(
                        TransactionSynchronization.STATUS_COMMITTED);
            } finally {
                TransactionSynchronizationManager.clear();
            }
        }
    }

    private Cache productionCache(RedisConnectionFactory factory) {
        CourseCacheConfig config = new CourseCacheConfig();
        var builder = RedisCacheManager.builder(factory)
                .cacheDefaults(config.redisCacheConfiguration(Duration.ofMinutes(10)));
        var beans = new StaticListableBeanFactory(Map.of("redisConnectionFactory", factory));
        config.transactionAwareCacheManager(beans.getBeanProvider(RedisConnectionFactory.class))
                .customize(builder);
        RedisCacheManager manager = builder.build();
        manager.afterPropertiesSet();
        Cache cache = manager.getCache("courseDetails");
        assertNotNull(cache);
        return cache;
    }

    private CourseResponse response() {
        return new CourseResponse(7L, 1L, "Category", "Committed title", "Description",
                new BigDecimal("100.00"), "BEGINNER", "PUBLISHED");
    }

    /** Both driver APIs exist, but a reactive SET needs an explicit acknowledgement. */
    private static final class RedisCommands implements AutoCloseable {
        final Map<String, byte[]> values = new ConcurrentHashMap<>();
        final CompletableFuture<Boolean> reactiveAcknowledgement = new CompletableFuture<>();
        final RedisConnectionFactory factory = mock(RedisConnectionFactory.class,
                withSettings().extraInterfaces(ReactiveRedisConnectionFactory.class));

        RedisCommands() {
            RedisConnection connection = mock(RedisConnection.class);
            RedisStringCommands strings = mock(RedisStringCommands.class);
            when(factory.getConnection()).thenReturn(connection);
            when(connection.stringCommands()).thenReturn(strings);
            when(strings.set(any(byte[].class), any(byte[].class), any(SetCondition.class),
                    any(Expiration.class))).thenAnswer(call -> {
                values.put(key(call.getArgument(0)), call.getArgument(1));
                return true;
            });
            when(strings.get(any(byte[].class))).thenAnswer(call -> values.get(key(call.getArgument(0))));

            ReactiveRedisConnection reactive = mock(ReactiveRedisConnection.class);
            ReactiveStringCommands reactiveStrings = mock(ReactiveStringCommands.class);
            when(((ReactiveRedisConnectionFactory) factory).getReactiveConnection()).thenReturn(reactive);
            when(reactive.stringCommands()).thenReturn(reactiveStrings);
            when(reactive.closeLater()).thenReturn(Mono.empty());
            when(reactiveStrings.set(any(ByteBuffer.class), any(ByteBuffer.class),
                    any(SetCondition.class), any(Expiration.class)))
                    .thenReturn(Mono.fromFuture(reactiveAcknowledgement));
        }

        private static String key(byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }

        @Override public void close() {
            reactiveAcknowledgement.complete(true);
        }
    }
}
