package com.ccnlthd.course_management.config;

import org.aopalliance.intercept.MethodInvocation;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.locks.ReentrantLock;

/**
 * Serializes Course detail cache reads and Course/Category writes in one JVM.
 * The advisor must wrap BOTH cache advice and transaction advice.
 */
public final class CourseConsistencyGate {
    private final boolean enabled;
    private final ReentrantLock lock = new ReentrantLock(true);

    public CourseConsistencyGate(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** Diagnostic observation used by the concurrency test. */
    public boolean hasQueuedThread(Thread thread) {
        return lock.hasQueuedThread(thread);
    }

    public Object invoke(MethodInvocation invocation) throws Throwable {
        if (!enabled) return invocation.proceed();
        lock.lockInterruptibly();
        boolean releaseAtTransactionCompletion = false;
        try {
            // An existing outer TransactionTemplate may commit AFTER this method.
            // Keep the lock until its afterCommit cache callbacks have finished.
            if (TransactionSynchronizationManager.isActualTransactionActive()
                    && TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(
                        new TransactionSynchronization() {
                            @Override
                            public void afterCompletion(int status) {
                                lock.unlock();
                            }
                        });
                releaseAtTransactionCompletion = true;
            }
            return invocation.proceed();
        } finally {
            if (!releaseAtTransactionCompletion) lock.unlock();
        }
    }
}
