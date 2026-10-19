package com.ccnlthd.course_management;

import com.ccnlthd.course_management.controller.CourseController;
import com.ccnlthd.course_management.controller.EnrollmentController;
import com.ccnlthd.course_management.exception.GlobalExceptionHandler;
import com.ccnlthd.course_management.service.CourseService;
import com.ccnlthd.course_management.service.EnrollmentService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Real Spring Services and an isolated H2 database. No outer test transaction hides committed writes. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:cv2_validation;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.defer-datasource-initialization=true",
        "spring.sql.init.mode=always",
        "spring.sql.init.data-locations=classpath:test-data.sql",
        "spring.flyway.enabled=false",
        "spring.cache.type=none"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class EnrollmentValidationDatabaseTests {
    @Autowired private EnrollmentService enrollments;
    @Autowired private CourseService courses;
    @Autowired private JdbcTemplate jdbc;
    private LocalValidatorFactoryBean validator;
    private MockMvc mvc;

    @BeforeEach
    void setUp() throws Exception {
        String url;
        try (var connection = jdbc.getDataSource().getConnection()) {
            url = connection.getMetaData().getURL();
            assertTrue(url.startsWith("jdbc:h2:mem:cv2_validation"), "This test must use its private H2 database");
            System.out.printf("CV2_DATABASE product=%s version=%s url=%s autoCommit=%s%n",
                    connection.getMetaData().getDatabaseProductName(), connection.getMetaData().getDatabaseProductVersion(),
                    url, connection.getAutoCommit());
        }
        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mvc = MockMvcBuilders.standaloneSetup(new EnrollmentController(enrollments), new CourseController(courses))
                .setControllerAdvice(new GlobalExceptionHandler()).setValidator(validator).build();
    }

    @AfterEach
    void closeValidator() { if (validator != null) validator.close(); }

    static Stream<Arguments> invalidEnrollments() {
        return EnrollmentValidationContractTests.enrollmentCases()
                .filter(c -> Set.of("missing-all", "zero-student", "negative-course", "unknown-status").contains(c.name()))
                .flatMap(c -> Stream.of("POST", "PUT").map(m -> Arguments.of(m, c)));
    }

    @ParameterizedTest(name = "{0} leaves H2 unchanged: {1}")
    @MethodSource("invalidEnrollments")
    void invalidEnrollmentDoesNotChangeCommittedRows(String method,
            EnrollmentValidationContractTests.InvalidCase test) throws Exception {
        unchanged("enrollment-" + method + "-" + test.name(), method, "/api/enrollments", test.body(), test.fields());
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"POST", "PUT"})
    void invalidCourseReferenceDoesNotChangeCommittedRows(String method) throws Exception {
        unchanged("course-" + method + "-category-zero", method, "/api/courses",
                EnrollmentValidationContractTests.courseBody("0"), Set.of("categoryId"));
    }

    @Test
    void validUpdateControlReachesRealServiceAndChangesDatabase() throws Exception {
        String previous = jdbc.queryForObject("SELECT status FROM enrollments WHERE id=1", String.class);
        String next = "COMPLETED".equals(previous) ? "ACTIVE" : "COMPLETED";
        try {
            String body = EnrollmentValidationContractTests.body("1", "1", next);
            MvcResult result = mvc.perform(put("/api/enrollments/1").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isOk()).andReturn();
            assertEquals(next, jdbc.queryForObject("SELECT status FROM enrollments WHERE id=1", String.class));
            EnrollmentValidationContractTests.exchange("database/valid-update-control", "PUT", "/api/enrollments", body, result);
            System.out.printf("CV2_DATABASE_CONTROL_PASS id=1 before=%s after=%s realService=true%n", previous, next);
        } finally {
            // Restore only this test's private H2 fixture; never connects to a user's MySQL.
            jdbc.update("UPDATE enrollments SET status=? WHERE id=1", previous);
        }
    }

    private void unchanged(String label, String method, String root, String body, Set<String> fields) throws Exception {
        Map<String, List<Map<String, Object>>> before = snapshot();
        assertFalse(before.get("enrollments").isEmpty(), "Seed must be present before comparing rows");
        MvcResult result = mvc.perform(EnrollmentValidationContractTests.request(method, root)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
        Map<String, List<Map<String, Object>>> after = snapshot();
        EnrollmentValidationContractTests.exchange("database/" + label, method, root, body, result);
        EnrollmentValidationContractTests.evidence("database/" + label + "-rows",
                "database=isolated H2; real Services; no outer test transaction\n"
                        + "before=" + before + "\nafter=" + after + "\nrowsEqual=" + before.equals(after) + "\n");
        assertAll(label,
                () -> assertEquals(400, result.getResponse().getStatus()),
                () -> assertEquals(before, after, "Invalid request changed committed rows"));
        System.out.printf("CV2_DATABASE_UNCHANGED_PASS case=%s tables=4 rowsEqual=true http=400%n", label);
    }

    private Map<String, List<Map<String, Object>>> snapshot() {
        Map<String, List<Map<String, Object>>> rows = new LinkedHashMap<>();
        for (String table : List.of("categories", "courses", "students", "enrollments")) {
            rows.put(table, jdbc.queryForList("SELECT * FROM " + table + " ORDER BY id"));
        }
        return rows;
    }
}
