package com.ccnlthd.course_management;

import com.ccnlthd.course_management.controller.CourseController;
import com.ccnlthd.course_management.dto.request.CourseRequest;
import com.ccnlthd.course_management.dto.response.CourseResponse;
import com.ccnlthd.course_management.service.CourseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CourseControllerValidationTests {

    private CourseService courseService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        courseService = mock(CourseService.class);

        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders
                .standaloneSetup(new CourseController(courseService))
                .setValidator(validator)
                .build();
    }

    @Test
    void shouldReturn400AndNotCallBusinessLogicWhenRequestIsInvalid() throws Exception {
        String invalidJson = """
                {
                  "categoryId": null,
                  "title": "",
                  "description": "Invalid course",
                  "price": -1,
                  "level": "",
                  "status": ""
                }
                """;

        mockMvc.perform(post("/api/courses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(courseService);
    }

    @Test
    void shouldCallBusinessLogicWhenRequestIsValid() throws Exception {
        CourseResponse response = new CourseResponse(
                10L,
                1L,
                "Backend",
                "Spring Boot Fundamentals",
                "Build REST APIs with Spring Boot.",
                new BigDecimal("499000.00"),
                "BEGINNER",
                "PUBLISHED"
        );

        when(courseService.createCourse(any(CourseRequest.class)))
                .thenReturn(response);

        String validJson = """
                {
                  "categoryId": 1,
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
                .andExpect(status().isCreated());

        verify(courseService, times(1))
                .createCourse(any(CourseRequest.class));
    }
}
