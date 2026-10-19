package com.ccnlthd.course_management;

import com.ccnlthd.course_management.controller.CourseController;
import com.ccnlthd.course_management.controller.EnrollmentController;
import com.ccnlthd.course_management.dto.request.EnrollmentRequest;
import com.ccnlthd.course_management.dto.response.EnrollmentResponse;
import com.ccnlthd.course_management.exception.GlobalExceptionHandler;
import com.ccnlthd.course_management.service.CourseService;
import com.ccnlthd.course_management.service.EnrollmentService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Request validation boundary with mocked Services; no database is used here. */
class EnrollmentValidationContractTests {
    record InvalidCase(String name, String body, Set<String> fields) { }

    private EnrollmentService enrollments;
    private CourseService courses;
    private LocalValidatorFactoryBean validator;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        enrollments = mock(EnrollmentService.class);
        courses = mock(CourseService.class);
        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mvc = MockMvcBuilders.standaloneSetup(
                        new EnrollmentController(enrollments), new CourseController(courses))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator).build();
    }

    @AfterEach
    void closeValidator() { if (validator != null) validator.close(); }

    static Stream<InvalidCase> enrollmentCases() {
        return Stream.of(
                new InvalidCase("missing-all", "{}", Set.of("studentId", "courseId", "status")),
                new InvalidCase("null-student", body("null", "3", "ACTIVE"), Set.of("studentId")),
                new InvalidCase("zero-student", body("0", "3", "ACTIVE"), Set.of("studentId")),
                new InvalidCase("negative-student", body("-1", "3", "ACTIVE"), Set.of("studentId")),
                new InvalidCase("null-course", body("2", "null", "ACTIVE"), Set.of("courseId")),
                new InvalidCase("zero-course", body("2", "0", "ACTIVE"), Set.of("courseId")),
                new InvalidCase("negative-course", body("2", "-1", "ACTIVE"), Set.of("courseId")),
                new InvalidCase("null-status", "{\"studentId\":2,\"courseId\":3,\"status\":null}", Set.of("status")),
                new InvalidCase("blank-status", body("2", "3", ""), Set.of("status")),
                new InvalidCase("unknown-status", body("2", "3", "CANCELLED"), Set.of("status")),
                new InvalidCase("lowercase-status", body("2", "3", "active"), Set.of("status")),
                new InvalidCase("padded-status", body("2", "3", "ACTIVE "), Set.of("status")),
                new InvalidCase("oversized-status", body("2", "3", "A".repeat(51)), Set.of("status")));
    }

    static Stream<Arguments> invalidEnrollments() {
        return enrollmentCases().flatMap(c -> Stream.of("POST", "PUT").map(m -> Arguments.of(m, c)));
    }

    static Stream<Arguments> invalidCategories() {
        return Stream.of("null", "0", "-1", "missing")
                .flatMap(id -> Stream.of("POST", "PUT").map(m -> Arguments.of(m, id)));
    }

    static Stream<Arguments> validEnrollments() {
        return Stream.of("ACTIVE", "COMPLETED")
                .flatMap(s -> Stream.of("POST", "PUT").map(m -> Arguments.of(m, s)));
    }

    @ParameterizedTest(name = "{0} invalid enrollment {1}")
    @MethodSource("invalidEnrollments")
    void rejectsEnrollmentBeforeCallingService(String method, InvalidCase test) throws Exception {
        rejects(mvc, "web/" + method + "-" + test.name(), method, "/api/enrollments", test.body(), test.fields());
        verifyNoInteractions(enrollments, courses);
        System.out.printf("CV2_SERVICE_BOUNDARY_PASS method=%s case=%s interactions=0%n", method, test.name());
    }

    @ParameterizedTest(name = "{0} categoryId={1}")
    @MethodSource("invalidCategories")
    void requiresPositiveCourseCategoryBeforeCallingService(String method, String categoryId) throws Exception {
        rejects(mvc, "web/" + method + "-category-" + categoryId, method, "/api/courses",
                courseBody(categoryId), Set.of("categoryId"));
        verifyNoInteractions(enrollments, courses);
        System.out.printf("CV2_SERVICE_BOUNDARY_PASS method=%s categoryId=%s interactions=0%n", method, categoryId);
    }

    @ParameterizedTest(name = "{0} accepts {1}")
    @MethodSource("validEnrollments")
    void acceptsBothStatusesAndDispatchesToService(String method, String enrollmentStatus) throws Exception {
        EnrollmentResponse response = new EnrollmentResponse(1L, 2L, 3L, enrollmentStatus,
                LocalDateTime.of(2026, 1, 1, 10, 0));
        when(enrollments.createEnrollment(any(EnrollmentRequest.class))).thenReturn(response);
        when(enrollments.updateEnrollment(eq(1L), any(EnrollmentRequest.class))).thenReturn(response);
        String json = body("2", "3", enrollmentStatus);
        MvcResult result = mvc.perform(request(method, "/api/enrollments").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().is("POST".equals(method) ? 201 : 200))
                .andExpect(jsonPath("$.status").value(enrollmentStatus)).andReturn();
        if ("POST".equals(method)) verify(enrollments).createEnrollment(any(EnrollmentRequest.class));
        else verify(enrollments).updateEnrollment(eq(1L), any(EnrollmentRequest.class));
        verifyNoMoreInteractions(enrollments);
        verifyNoInteractions(courses);
        exchange("web/" + method + "-valid-" + enrollmentStatus, method, "/api/enrollments", json, result);
        System.out.printf("CV2_VALID_PASS method=%s status=%s serviceDispatch=1%n", method, enrollmentStatus);
    }

    static MockHttpServletRequestBuilder request(String method, String root) {
        return "POST".equals(method) ? post(root) : put(root + "/1");
    }

    static String body(String student, String course, String enrollmentStatus) {
        return "{\"studentId\":%s,\"courseId\":%s,\"status\":\"%s\"}".formatted(student, course, enrollmentStatus);
    }

    static String courseBody(String category) {
        String prefix = "missing".equals(category) ? "" : "\"categoryId\":" + category + ",";
        return "{" + prefix + "\"title\":\"Validation course\",\"description\":\"Contract test\","
                + "\"price\":100,\"level\":\"BEGINNER\",\"status\":\"DRAFT\"}";
    }

    static MvcResult rejects(MockMvc mvc, String label, String method, String root,
                             String body, Set<String> fields) throws Exception {
        MvcResult result = mvc.perform(request(method, root).contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
        // Persist the actual result before assertions, so failed baseline cases remain reviewable.
        exchange(label, method, root, body, result);
        assertEquals(400, result.getResponse().getStatus(), label);
        MethodArgumentNotValidException error = assertInstanceOf(MethodArgumentNotValidException.class,
                result.getResolvedException(), label);
        assertEquals(fields, error.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField()).collect(Collectors.toSet()), label);
        mvcResultChecks(result);
        return result;
    }

    private static void mvcResultChecks(MvcResult result) throws Exception {
        jsonPath("$.code").value("VALIDATION_FAILED").match(result);
        jsonPath("$.message").value("Request data is invalid").match(result);
        jsonPath("$.timestamp").exists().match(result);
        jsonPath("$.result").isArray().match(result);
        jsonPath("$.result[*].message").value(everyItem(not(emptyOrNullString()))).match(result);
    }

    static void exchange(String label, String method, String root, String body, MvcResult result) throws IOException {
        evidence(label, "transport=MockMvc (in-process; not a network HTTP exchange)\nmethod=" + method
                + "\npath=" + root + ("PUT".equals(method) ? "/1" : "") + "\nrequest=" + body
                + "\nresponseStatus=" + result.getResponse().getStatus()
                + "\nresponse=" + result.getResponse().getContentAsString(StandardCharsets.UTF_8) + "\n");
    }

    static void evidence(String label, String content) throws IOException {
        String directory = System.getProperty("cv2.evidenceDir");
        if (directory == null || directory.isBlank()) return;
        Path file = Path.of(directory).resolve(label + ".txt");
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }
}
