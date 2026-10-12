package com.ccnlthd.course_management.lab.m04;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.beans.factory.UnsatisfiedDependencyException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Primary;
import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(OrderAnnotation.class)
class M04DependencySelectionTest {
    // Normal Maven/CI runs test the final source with @Primary.
    // The external script first removes @Primary and runs -Dm04.phase=ambiguous.
    private static String phase() {
        String value = System.getProperty("m04.phase", "primary");
        if (!List.of("ambiguous", "primary").contains(value)) {
            throw new IllegalArgumentException("Unknown m04.phase: " + value);
        }
        return value;
    }

    @Test
    @Order(1)
    void dependencySelection() {
        String mode = phase();
        boolean primary = StandardPolicy.class.isAnnotationPresent(Primary.class);
        System.out.println("[M04] BEGIN " + mode);
        System.out.println("[M04] JVM=" + System.getProperty("java.version"));
        System.out.println("[M04] profile=m04; context=Spring; HTTP=none");
        System.out.println("[M04] StandardPolicy @Primary=" + primary);
        assertEquals(mode.equals("primary"), primary,
                "Source annotation must match the phase requested by the script");

        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles("m04");
            context.scan(PricePolicy.class.getPackageName());
            String[] names = context.getBeanFactory().getBeanNamesForType(PricePolicy.class);
            Arrays.sort(names);
            assertArrayEquals(new String[] {"discountPolicy", "standardPolicy"}, names);
            System.out.println("[M04] candidates=" + String.join(",", names));

            if (mode.equals("ambiguous")) {
                UnsatisfiedDependencyException failure = assertThrows(
                        UnsatisfiedDependencyException.class, context::refresh);
                NoUniqueBeanDefinitionException cause = ambiguityCause(failure);
                assertNotNull(cause, "Startup must fail because two PricePolicy beans match");
                assertEquals(2, cause.getNumberOfBeansFound());
                assertNotNull(cause.getBeanNamesFound());
                assertTrue(cause.getBeanNamesFound().containsAll(List.of(names)));
                assertFalse(context.isActive());
                System.out.println("[M04] SPRING_CONTEXT_STARTUP_FAILED (expected)");
                System.out.println(failure.getClass().getName() + ": " + failure.getMessage());
                System.out.println("Caused by: " + cause.getClass().getName()
                        + ": " + cause.getMessage());
                System.out.println("[M04] expected-error assertion=PASS");
            } else {
                context.refresh();
                PolicyProbe probe = context.getBean(PolicyProbe.class);
                assertTrue(context.isActive());
                assertEquals("standard", probe.selectedPolicy().code());
                assertEquals("discount", probe.qualifiedPolicy().code());
                assertSame(context.getBean("standardPolicy"), probe.selectedPolicy());
                assertSame(context.getBean("discountPolicy"), probe.qualifiedPolicy());
                System.out.println("[M04] SPRING_CONTEXT_STARTED");
                System.out.println("[M04] selection assertions=PASS");
            }
        }
        System.out.println("[M04] END " + mode);
    }

    @Test
    @Order(2)
    void qualifierSelectsDiscount() {
        System.out.println("[M04] BEGIN qualifier");
        System.out.println("[M04] StandardPolicy @Primary="
                + StandardPolicy.class.isAnnotationPresent(Primary.class));
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles("m04");
            context.register(StandardPolicy.class, DiscountPolicy.class, QualifiedOnlyProbe.class);
            context.refresh();
            QualifiedOnlyProbe probe = context.getBean(QualifiedOnlyProbe.class);
            assertEquals(2, context.getBeansOfType(PricePolicy.class).size());
            assertSame(context.getBean("discountPolicy"), probe.policy);
            assertEquals("discount", probe.policy.code());
            System.out.println("[M04] qualifier-only=" + probe.policy.code());
            System.out.println("[M04] qualifier assertion=PASS");
        }
        System.out.println("[M04] END qualifier");
    }

    private static NoUniqueBeanDefinitionException ambiguityCause(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof NoUniqueBeanDefinitionException ambiguity) {
                return ambiguity;
            }
        }
        return null;
    }

    // Registered only in qualifierSelectsDiscount, not by component scanning.
    static class QualifiedOnlyProbe {
        private final PricePolicy policy;

        QualifiedOnlyProbe(@Qualifier("discountPolicy") PricePolicy policy) {
            this.policy = policy;
        }
    }
}
