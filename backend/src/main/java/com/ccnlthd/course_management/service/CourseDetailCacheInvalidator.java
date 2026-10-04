package com.ccnlthd.course_management.service;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
@RequiredArgsConstructor
public class CourseDetailCacheInvalidator {

    private static final String CACHE_NAME = "courseDetails";

    private final CacheManager cacheManager;

    public void evictAfterCommit(Long courseId) {
        afterCommit(() -> cache().evictIfPresent(courseId));
    }

    public void clearAfterCommit() {
        afterCommit(() -> cache().invalidate());
    }

    private Cache cache() {
        Cache cache = cacheManager.getCache(CACHE_NAME);
        if (cache == null) {
            throw new IllegalStateException("Missing cache: " + CACHE_NAME);
        }
        return cache;
    }

    private void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}
