package com.ccnlthd.course_management;

import com.ccnlthd.course_management.controller.CourseController;
import com.ccnlthd.course_management.dto.request.CourseRequest;
import com.ccnlthd.course_management.dto.response.CourseResponse;
import com.ccnlthd.course_management.exception.GlobalExceptionHandler;
import com.ccnlthd.course_management.service.CourseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void shouldReturn400AndNotCallBusinessLogicWhenRequestIsInvalid() throws Exception {
        String invalidJson = """
                {
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

    @Test
    void shouldListCourses() throws Exception {
        when(courseService.getAllCourses()).thenReturn(List.of(new CourseResponse(
                10L, "Spring Boot", null,
                BigDecimal.ZERO, "BEGINNER", "PUBLISHED"
        )));

        mockMvc.perform(get("/api/courses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(10))
                .andExpect(jsonPath("$[0].title").value("Spring Boot"));
    }

    @Test
    void shouldRejectInvalidUpdateBeforeCallingService() throws Exception {
        mockMvc.perform(put("/api/courses/{id}", 10L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\",\"price\":-1}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(courseService);
    }

    @Test
    void shouldUpdateCourse() throws Exception {
        when(courseService.updateCourse(any(Long.class), any(CourseRequest.class)))
                .thenReturn(new CourseResponse(
                        10L, "Updated course", null,
                        BigDecimal.ZERO, "BEGINNER", "PUBLISHED"
                ));

        mockMvc.perform(put("/api/courses/{id}", 10L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Updated course","price":0,
                                 "level":"BEGINNER","status":"PUBLISHED"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated course"));
    }

    @Test
    void shouldReturnNoContentAfterDelete() throws Exception {
        mockMvc.perform(delete("/api/courses/{id}", 10L))
                .andExpect(status().isNoContent());

        verify(courseService).deleteCourse(10L);
    }

}
