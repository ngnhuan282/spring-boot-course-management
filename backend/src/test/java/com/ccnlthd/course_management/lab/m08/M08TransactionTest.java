package com.ccnlthd.course_management.lab.m08;

import com.ccnlthd.course_management.entity.Category;
import com.ccnlthd.course_management.entity.Course;
import com.ccnlthd.course_management.repository.CategoryRepository;
import com.ccnlthd.course_management.repository.CourseRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

// M08 CODE BEGIN context
@SpringBootTest(properties = {
        "spring.cache.type=none",
        "spring.sql.init.mode=never",
        "spring.jpa.hibernate.ddl-auto=update",
        "spring.jpa.defer-datasource-initialization=false",
        "spring.jpa.open-in-view=false",
        "spring.jpa.show-sql=false",
        "spring.jpa.properties.hibernate.format_sql=false",
        "logging.level.org.hibernate.SQL=DEBUG",
        "logging.level.org.hibernate.orm.jdbc.bind=TRACE",
        "logging.level.org.springframework.transaction.interceptor=TRACE",
        "logging.level.org.springframework.orm.jpa.JpaTransactionManager=DEBUG"
})
@Import(M08TransactionTest.ProbeConfig.class)
class M08TransactionTest {
    @Autowired FailingService injectedService;
    @Autowired CourseRepository courses;
    @Autowired CategoryRepository categories;
    @Autowired DataSource dataSource;

    private String title;
    private Long categoryId;

    // Collector: isolated MySQL. Normal Maven: the existing test database.
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        String url = System.getenv("M08_JDBC_URL");
        if (url != null && !url.isBlank()) {
            properties.add("spring.datasource.url", () -> url);
            properties.add("spring.datasource.driver-class-name",
                    () -> "com.mysql.cj.jdbc.Driver");
            properties.add("spring.datasource.username",
                    () -> System.getenv("M08_DB_USER"));
            properties.add("spring.datasource.password",
                    () -> System.getenv("M08_DB_PASSWORD"));
        }
    }
    // M08 CODE END context

    // M08 CODE BEGIN cases
    // No test transaction surrounds either service call.
    @Test
    void proxyRollsBack() throws SQLException {
        verify(injectedService, "proxy", true, 0);
    }

    @Test
    void directNewCommitsRepository() throws SQLException {
        verify(new FailingService(courses), "new", false, 1);
    }
    // M08 CODE END cases

    // M08 CODE BEGIN verification
    private void verify(FailingService service, String mode,
                        boolean proxy, int expectedRows) throws SQLException {
        assertFalse(
                TransactionSynchronizationManager.isActualTransactionActive());
        assertEquals(proxy, AopUtils.isAopProxy(service));
        assertTrue(AopUtils.isAopProxy(courses));
        String marker = System.getenv().getOrDefault(
                "M08_MARKER", "M08_" + UUID.randomUUID());
        title = marker + "_" + mode.toUpperCase();
        Category category = new Category();
        category.setName(title + "_CAT");
        category.setDescription("M08 isolated test category");
        category = categories.saveAndFlush(category);
        categoryId = category.getId();
        Course course = new Course();
        course.setCategory(category);
        course.setTitle(title);
        course.setDescription("M08 transaction integration test");
        course.setPrice(new BigDecimal("100000.00"));
        course.setLevel("BEGINNER");
        course.setStatus("DRAFT");

        System.out.printf(
                "M08_BEGIN mode=%s title=%s serviceProxy=%s "
                        + "repositoryProxy=%s callerTx=%s%n",
                mode, title,
                AopUtils.isAopProxy(service), AopUtils.isAopProxy(courses),
                TransactionSynchronizationManager.isActualTransactionActive());
        try (var connection = dataSource.getConnection()) {
            System.out.printf("M08_DATABASE product=%s url=%s autoCommit=%s%n",
                    connection.getMetaData().getDatabaseProductName(),
                    connection.getMetaData().getURL(),
                    connection.getAutoCommit());
        }
        long before = countRows();
        assertEquals(0, before);
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.saveThenFail(course));
        System.out.printf("M08_EXCEPTION class=%s message=%s%n",
                error.getClass().getName(), error.getMessage());
        error.printStackTrace(System.out);
        assertEquals("M08_TX_PROBE", error.getMessage());
        assertNotNull(course.getId());
        assertFalse(
                TransactionSynchronizationManager.isActualTransactionActive());
        long after = countRows();
        System.out.printf(
                "M08_RESULT mode=%s title=%s categoryId=%d "
                        + "id=%d before=%d after=%d%n",
                mode, title, categoryId, course.getId(), before, after);
        assertEquals(expectedRows, after);
        System.out.printf("M08_END mode=%s%n", mode);
    }

    // Separate auto-commit JDBC reads outside the service transaction.
    private long countRows() throws SQLException {
        assertFalse(
                TransactionSynchronizationManager.isActualTransactionActive());
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM courses WHERE title=?")) {
            assertTrue(connection.getAutoCommit());
            statement.setString(1, title);
            try (var rows = statement.executeQuery()) {
                assertTrue(rows.next());
                long count = rows.getLong(1);
                System.out.printf("M08_DB_READ title=%s rows=%d autoCommit=%s%n",
                        title, count, connection.getAutoCommit());
                return count;
            }
        }
    }
    // M08 CODE END verification

    @AfterEach
    void cleanup() {
        // Collector reads MySQL before removing its private container.
        if (!Boolean.parseBoolean(System.getenv("M08_KEEP_ROWS"))) {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            if (title != null)
                jdbc.update("DELETE FROM courses WHERE title=?", title);
            if (categoryId != null)
                jdbc.update("DELETE FROM categories WHERE id=?", categoryId);
        }
    }

    // M08 CODE BEGIN service
    @TestConfiguration(proxyBeanMethods = false)
    static class ProbeConfig {
        @Bean
        FailingService failingService(CourseRepository repository) {
            return new FailingService(repository);
        }
    }

    public static class FailingService {
        private final CourseRepository repository;

        public FailingService(CourseRepository repository) {
            this.repository = repository;
        }

        @Transactional
        public void saveThenFail(Course course) {
            System.out.printf("M08_ENTRY title=%s txActive=%s%n",
                    course.getTitle(),
                TransactionSynchronizationManager.isActualTransactionActive());
            repository.saveAndFlush(course);
            System.out.printf("M08_AFTER_FLUSH id=%d title=%s txActive=%s%n",
                    course.getId(), course.getTitle(),
                TransactionSynchronizationManager.isActualTransactionActive());
            throw new IllegalStateException("M08_TX_PROBE");
        }
    }
    // M08 CODE END service
}
