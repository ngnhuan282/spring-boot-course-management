package com.ccnlthd.course_management.lab.m05;

import javax.sql.DataSource;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("m05-manual")
public class M05DataSourceConfig {
    @Bean
    DataSource m05DataSource(DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().build();
    }
}
