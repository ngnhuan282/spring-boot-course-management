package com.ccnlthd.course_management.config;

import com.ccnlthd.course_management.service.CategoryService;
import com.ccnlthd.course_management.service.CourseService;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.Advisor;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.StaticMethodMatcherPointcut;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;

import java.lang.reflect.Method;
import java.util.Set;

@Configuration(proxyBeanMethods = false)
public class CourseConsistencyConfiguration {
    @Bean
    CourseConsistencyGate courseConsistencyGate(Environment environment) {
        return new CourseConsistencyGate(environment.getProperty(
                "course.cache.concurrent-guard-enabled", Boolean.class, true));
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    Advisor courseConsistencyAdvisor(CourseConsistencyGate gate) {
        var pointcut = new StaticMethodMatcherPointcut() {
            @Override
            public boolean matches(Method method, Class<?> targetClass) {
                if (CourseService.class.isAssignableFrom(targetClass)) {
                    return Set.of("getCourseById", "createCourse",
                            "updateCourse", "deleteCourse").contains(method.getName());
                }
                if (CategoryService.class.isAssignableFrom(targetClass)) {
                    return Set.of("createCategory", "updateCategory", "deleteCategory")
                            .contains(method.getName());
                }
                return false;
            }
        };
        var advisor = new DefaultPointcutAdvisor(pointcut,
                (MethodInterceptor) gate::invoke);
        advisor.setOrder(Ordered.HIGHEST_PRECEDENCE + 100);
        return advisor;
    }
}
