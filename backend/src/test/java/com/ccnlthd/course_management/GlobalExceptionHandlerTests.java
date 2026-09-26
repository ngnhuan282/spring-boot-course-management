package com.ccnlthd.course_management;

import com.ccnlthd.course_management.controller.CourseController;
import com.ccnlthd.course_management.dto.request.CourseRequest;
import com.ccnlthd.course_management.exception.AppException;
import com.ccnlthd.course_management.exception.ErrorCode;
import com.ccnlthd.course_management.exception.GlobalExceptionHandler;
import com.ccnlthd.course_management.service.CourseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTests {

    private CourseService courseService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        courseService = mock(CourseService.class);

        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders
                .standaloneSetup(new CourseController(courseService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void shouldReturnStandardResponseWhenBusinessExceptionOccurs() throws Exception {
        when(courseService.getCourseById(99L))
                .thenThrow(new AppException(ErrorCode.COURSE_NOT_FOUND));

        mockMvc.perform(get("/api/courses/{id}", 99L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COURSE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Course not found"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void shouldReturnStandardResponseAndSkipServiceWhenValidationFails() throws Exception {
        String invalidJson = """
                {
                  "title": "",
                  "price": -1,
                  "level": "",
                  "status": ""
                }
                """;

        mockMvc.perform(post("/api/courses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.timestamp").exists());

        verifyNoInteractions(courseService);
    }

    @Test
    void shouldReturnStandardResponseWhenUnexpectedExceptionOccurs() throws Exception {
        when(courseService.createCourse(any(CourseRequest.class)))
                .thenThrow(new RuntimeException("Database is unavailable"));

        String validJson = """
                {
                  "title": "Spring Boot Fundamentals",
                  "description": "Build REST APIs with Spring Boot.",
                  "price": 499000,
                  "level": "BEGINNER",
                  "status": "PUBLISHED"
                }
                """;

        mockMvc.perform(post("/api/courses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("UNCATEGORIZED_EXCEPTION"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"))
                .andExpect(jsonPath("$.timestamp").exists());
    }
}
