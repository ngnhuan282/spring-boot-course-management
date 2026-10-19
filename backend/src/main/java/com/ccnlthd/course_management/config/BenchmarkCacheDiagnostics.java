package com.ccnlthd.course_management.config;

import org.springframework.boot.ApplicationRunner;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
@Profile("benchmark")
public class BenchmarkCacheDiagnostics {

    @Bean
    ApplicationRunner logBenchmarkCache(
            CacheManager cacheManager, Environment environment) {
        return args -> System.out.printf(
                "BENCH_CACHE configured=%s manager=%s profiles=%s%n",
                environment.getProperty("spring.cache.type"),
                cacheManager.getClass().getName(),
                String.join(",", environment.getActiveProfiles())
        );
    }
}