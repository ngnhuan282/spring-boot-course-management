package com.ccnlthd.course_management;

import com.ccnlthd.course_management.controller.CourseController;
import com.ccnlthd.course_management.dto.request.CourseRequest;
import com.ccnlthd.course_management.dto.response.CourseResponse;
import com.ccnlthd.course_management.exception.GlobalExceptionHandler;
import com.ccnlthd.course_management.service.CourseService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CourseControllerValidationTests {
    private CourseService courseService;
    private MockMvc mockMvc;
    private LocalValidatorFactoryBean validator;

    @BeforeEach
    void setUp() {
        courseService = mock(CourseService.class);
        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new CourseController(courseService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @AfterEach
    void closeValidator() {
        validator.close();
    }

    @Test
    void shouldReturn400AndNotCallBusinessLogicWhenRequestIsInvalid()
            throws Exception {
        rejects("multi-field", post("/api/courses"),
                json("", -1, "BEGINNER", "DRAFT"), "title", "price");
    }

    @Test
    void shouldRejectUnknownLevelBeforeCallingService() throws Exception {
        rejects("unknown-level", post("/api/courses"),
                json("Valid title", 100, "UNKNOWN", "DRAFT"), "level");
    }

    @Test
    void shouldRejectUnknownStatusBeforeCallingService() throws Exception {
        rejects("unknown-status", post("/api/courses"),
                json("Valid title", 100, "BEGINNER", "UNKNOWN"), "status");
    }

    @Test
    void shouldRejectInvalidUpdateBeforeCallingService() throws Exception {
        rejects("invalid-update", put("/api/courses/10"),
                json("", -1, "BEGINNER", "DRAFT"), "title", "price");
    }

    private void rejects(String name, MockHttpServletRequestBuilder request,
                         String body, String... fields) throws Exception {
        System.out.println("M09_WEB_BEGIN case=" + name);
        mockMvc.perform(request.contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("Request data is invalid"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.result").isArray())
                .andExpect(jsonPath("$.result[*].field")
                        .value(containsInAnyOrder(fields)))
                .andExpect(jsonPath("$.result[*].message")
                        .value(everyItem(not(emptyOrNullString()))));
        verifyNoInteractions(courseService);
        System.out.println("M09_WEB_PASS case=" + name
                + " serviceInteractions=0");
    }

    @Test
    void shouldCallBusinessLogicWhenRequestIsValid() throws Exception {
        CourseResponse response = new CourseResponse(10L, 1L, "Backend",
                "Valid title", "M09 validation test",
                new BigDecimal("100.00"), "BEGINNER", "DRAFT");
        when(courseService.createCourse(any(CourseRequest.class)))
                .thenReturn(response);
        System.out.println("M09_WEB_BEGIN case=valid");
        mockMvc.perform(post("/api/courses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("Valid title", 100, "BEGINNER", "DRAFT")))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10));
        verify(courseService, times(1)).createCourse(any(CourseRequest.class));
        System.out.println("M09_WEB_PASS case=valid serviceInteractions=1");
    }

    private String json(String title, int price, String level, String status) {
        return """
                {"categoryId":1,"title":"%s","description":"M09 validation test",
                 "price":%d,"level":"%s","status":"%s"}
                """.formatted(title, price, level, status);
    }
}
