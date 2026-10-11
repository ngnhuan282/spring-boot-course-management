package com.ccnlthd.course_management.lab.m03;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.atomic.AtomicInteger;

public class BeanLifecycleProbe {
    private static final AtomicInteger COUNTER = new AtomicInteger();
    private final int id = COUNTER.incrementAndGet();
    private boolean initialized;
    private boolean destroyed;

    public BeanLifecycleProbe() {
        System.out.println("constructor id=" + id);
    }

    @PostConstruct
    void init() {
        initialized = true;
        System.out.println("postConstruct id=" + id);
    }

    @PreDestroy
    void destroy() {
        destroyed = true;
        System.out.println("preDestroy id=" + id);
    }

    public boolean isInitialized() { return initialized; }
    public boolean isDestroyed() { return destroyed; }
}
