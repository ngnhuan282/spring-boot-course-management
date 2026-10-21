package com.ccnlthd.course_management.config;

import com.ccnlthd.course_management.dto.response.CourseResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.time.Duration;

@Configuration(proxyBeanMethods = false)
@EnableCaching(order = Ordered.LOWEST_PRECEDENCE - 1)
@EnableTransactionManagement
public class CourseCacheConfig {

    @Bean
    RedisCacheConfiguration redisCacheConfiguration(
            @Value("${spring.cache.redis.time-to-live:10m}") Duration ttl) {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(ttl)
                .disableCachingNullValues()
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
                        new JacksonJsonRedisSerializer<>(CourseResponse.class)));
    }

    @Bean
    RedisCacheManagerBuilderCustomizer transactionAwareCacheManager(
            ObjectProvider<RedisConnectionFactory> connectionFactories) {
        // The consistency gate must cover the completed Redis write. With Lettuce,
        // the default asynchronous writer can return before the SET is acknowledged.
        return builder -> builder
                .cacheWriter(RedisCacheWriter.create(connectionFactories.getObject(),
                        options -> options.immediateWrites()))
                .transactionAware();
    }
}
