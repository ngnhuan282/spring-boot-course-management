package com.ccnlthd.course_management.support;

import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration(proxyBeanMethods = false)
public class M15SqlTraceConfiguration {
    @Bean
    M15SqlTrace m15SqlTrace() {
        return new M15SqlTrace();
    }

    @Bean
    HibernatePropertiesCustomizer m15StatementInspector(M15SqlTrace trace) {
        return properties -> properties.put("hibernate.session_factory.statement_inspector", trace);
    }
}
