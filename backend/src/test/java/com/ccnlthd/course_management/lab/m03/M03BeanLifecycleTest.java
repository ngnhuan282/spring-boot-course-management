package com.ccnlthd.course_management.lab.m03;

import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class M03BeanLifecycleTest {

    // M03 CODE BEGIN singleton
    @Test @Order(1)
    void singleton() {
        checkManaged("singleton", SingletonConfig.class, true);
    }
    // M03 CODE END singleton

    // M03 CODE BEGIN prototype
    @Test @Order(2)
    void prototype() {
        checkManaged("prototype", PrototypeConfig.class, false);
    }
    // M03 CODE END prototype

    // M03 CODE BEGIN new
    @Test @Order(3)
    void plainNew() {
        System.out.println("[M03] BEGIN new");
        var bean = new BeanLifecycleProbe();
        System.out.println("initialized=" + bean.isInitialized());
        System.out.println("destroyed=" + bean.isDestroyed());
        assertFalse(bean.isInitialized());
        assertFalse(bean.isDestroyed());
        System.out.println("[M03] END new");
    }
    // M03 CODE END new

    // M03 CODE BEGIN shared
    private void checkManaged(String mode, Class<?> config, boolean singleton) {
        System.out.println("[M03] BEGIN " + mode);
        BeanLifecycleProbe first;
        BeanLifecycleProbe second;
        try (var context = new AnnotationConfigApplicationContext(config)) {
            first = context.getBean(BeanLifecycleProbe.class);
            second = context.getBean(BeanLifecycleProbe.class);
            System.out.println("same=" + (first == second));
            System.out.println("initialized(first)=" + first.isInitialized());
            System.out.println("initialized(second)=" + second.isInitialized());
            if (singleton) assertSame(first, second);
            else assertNotSame(first, second);
            assertTrue(first.isInitialized());
            assertTrue(second.isInitialized());
        } // The context closes here; no manual init()/destroy() calls.
        System.out.println("destroyed(first)=" + first.isDestroyed());
        System.out.println("destroyed(second)=" + second.isDestroyed());
        assertEquals(singleton, first.isDestroyed());
        assertEquals(singleton, second.isDestroyed());
        System.out.println("[M03] END " + mode);
    }
    // M03 CODE END shared

    // M03 CODE BEGIN singleton
    @Configuration(proxyBeanMethods = false)
    static class SingletonConfig {
        @Bean
        @Scope("singleton")
        BeanLifecycleProbe probe() { return new BeanLifecycleProbe(); }
    }
    // M03 CODE END singleton

    // M03 CODE BEGIN prototype
    @Configuration(proxyBeanMethods = false)
    static class PrototypeConfig {
        @Bean
        @Scope("prototype")
        BeanLifecycleProbe probe() { return new BeanLifecycleProbe(); }
    }
    // M03 CODE END prototype
}
