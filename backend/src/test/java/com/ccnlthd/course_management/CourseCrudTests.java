package com.ccnlthd.course_management;

import com.ccnlthd.course_management.controller.CourseController;
import com.ccnlthd.course_management.dto.request.CourseRequest;
import com.ccnlthd.course_management.dto.response.CourseResponse;
import com.ccnlthd.course_management.entity.Category;
import com.ccnlthd.course_management.entity.Course;
import com.ccnlthd.course_management.exception.AppException;
import com.ccnlthd.course_management.exception.ErrorCode;
import com.ccnlthd.course_management.exception.GlobalExceptionHandler;
import com.ccnlthd.course_management.repository.CategoryRepository;
import com.ccnlthd.course_management.repository.CourseRepository;
import com.ccnlthd.course_management.repository.EnrollmentRepository;
import com.ccnlthd.course_management.service.CourseService;
import com.ccnlthd.course_management.service.CourseDetailCacheInvalidator;
import com.ccnlthd.course_management.service.impl.CourseServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CourseCrudTests {

    private CourseRepository courseRepository;
    private CategoryRepository categoryRepository;
    private EnrollmentRepository enrollmentRepository;
    private CourseServiceImpl service;

    @BeforeEach
    void setUp() {
        courseRepository = mock(CourseRepository.class);
        categoryRepository = mock(CategoryRepository.class);
        enrollmentRepository = mock(EnrollmentRepository.class);
        service = new CourseServiceImpl(courseRepository, categoryRepository, enrollmentRepository,
                mock(CourseDetailCacheInvalidator.class));
    }

    @Test
    void listsCoursesAsResponseDtos() {
        Course course = course();
        when(courseRepository.findAll()).thenReturn(List.of(course));

        List<CourseResponse> responses = service.getAllCourses();

        assertEquals(1, responses.size());
        assertEquals(10L, responses.getFirst().getId());
        assertEquals("Backend", responses.getFirst().getCategoryName());
        assertEquals("Original", responses.getFirst().getTitle());
    }

    @Test
    void createsCourseWhenCategoryExists() {
        Category category = category(2L, "Database");
        when(categoryRepository.findById(2L)).thenReturn(Optional.of(category));
        when(courseRepository.save(any(Course.class))).thenAnswer(invocation -> {
            Course savedCourse = invocation.getArgument(0);
            savedCourse.setId(11L);
            return savedCourse;
        });

        CourseResponse response = service.createCourse(request());

        assertEquals(11L, response.getId());
        assertEquals(2L, response.getCategoryId());
        assertEquals("Database", response.getCategoryName());
        assertEquals("Updated", response.getTitle());
        assertEquals(new BigDecimal("100.00"), response.getPrice());
        verify(courseRepository).save(any(Course.class));
    }

    @Test
    void updatesExistingCourseAndCategory() {
        Course course = course();
        Category newCategory = category(2L, "Database");
        when(courseRepository.findById(10L)).thenReturn(Optional.of(course));
        when(categoryRepository.findById(2L)).thenReturn(Optional.of(newCategory));
        when(courseRepository.save(course)).thenReturn(course);

        CourseResponse response = service.updateCourse(10L, request());

        assertEquals(2L, response.getCategoryId());
        assertEquals("Updated", response.getTitle());
        assertEquals(new BigDecimal("100.00"), response.getPrice());
        verify(courseRepository).save(course);
    }

    @Test
    void rejectsUpdateWhenCourseOrCategoryIsMissing() {
        CourseRequest request = request();
        when(courseRepository.findById(10L)).thenReturn(Optional.empty());
        assertEquals(ErrorCode.COURSE_NOT_FOUND,
                assertThrows(AppException.class, () -> service.updateCourse(10L, request)).getErrorCode());
        verifyNoInteractions(categoryRepository);

        Course course = course();
        when(courseRepository.findById(10L)).thenReturn(Optional.of(course));
        when(categoryRepository.findById(2L)).thenReturn(Optional.empty());
        assertEquals(ErrorCode.CATEGORY_NOT_FOUND,
                assertThrows(AppException.class, () -> service.updateCourse(10L, request)).getErrorCode());
        assertEquals("Original", course.getTitle());
        verify(courseRepository, never()).save(any(Course.class));
    }

    @Test
    void deletesOnlyCoursesWithoutEnrollments() {
        Course course = course();
        when(courseRepository.findById(10L)).thenReturn(Optional.of(course));
        when(enrollmentRepository.existsByCourse_Id(10L)).thenReturn(false);

        service.deleteCourse(10L);

        verify(courseRepository).delete(course);
    }

    @Test
    void rejectsDeleteWhenCourseIsMissingOrHasEnrollments() {
        when(courseRepository.findById(10L)).thenReturn(Optional.empty());
        assertEquals(ErrorCode.COURSE_NOT_FOUND,
                assertThrows(AppException.class, () -> service.deleteCourse(10L)).getErrorCode());
        verifyNoInteractions(enrollmentRepository);

        when(courseRepository.findById(10L)).thenReturn(Optional.of(course()));
        when(enrollmentRepository.existsByCourse_Id(10L)).thenReturn(true);
        assertEquals(ErrorCode.COURSE_HAS_ENROLLMENTS,
                assertThrows(AppException.class, () -> service.deleteCourse(10L)).getErrorCode());
        verify(courseRepository, never()).delete(any(Course.class));
    }

    @Test
    void exposesListUpdateAndDeleteEndpoints() throws Exception {
        CourseService apiService = mock(CourseService.class);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new CourseController(apiService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
        CourseResponse response = new CourseResponse(10L, 2L, "Database", "Updated",
                "Description", new BigDecimal("100.00"), "BEGINNER", "PUBLISHED");
        when(apiService.getAllCourses()).thenReturn(List.of(response));
        when(apiService.updateCourse(any(Long.class), any(CourseRequest.class))).thenReturn(response);

        mvc.perform(get("/api/courses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(10))
                .andExpect(jsonPath("$[0].categoryName").value("Database"));
        mvc.perform(put("/api/courses/{id}", 10L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryId":2,"title":"Updated","description":"Description",
                                 "price":100,"level":"BEGINNER","status":"PUBLISHED"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated"));
        mvc.perform(delete("/api/courses/{id}", 10L))
                .andExpect(status().isNoContent());
        verify(apiService).deleteCourse(10L);
    }

    @Test
    void invalidUpdateDoesNotReachService() throws Exception {
        CourseService apiService = mock(CourseService.class);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new CourseController(apiService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();

        mvc.perform(put("/api/courses/{id}", 10L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryId\":2,\"title\":\"\",\"price\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        verifyNoInteractions(apiService);
    }

    @Test
    void deleteWithEnrollmentsReturnsConflict() throws Exception {
        CourseService apiService = mock(CourseService.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new CourseController(apiService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        doThrow(new AppException(ErrorCode.COURSE_HAS_ENROLLMENTS))
                .when(apiService).deleteCourse(10L);

        mvc.perform(delete("/api/courses/{id}", 10L))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COURSE_HAS_ENROLLMENTS"));
    }

    private Course course() {
        Course course = new Course();
        course.setId(10L);
        course.setCategory(category(1L, "Backend"));
        course.setTitle("Original");
        course.setPrice(BigDecimal.TEN);
        course.setLevel("BEGINNER");
        course.setStatus("DRAFT");
        return course;
    }

    private Category category(Long id, String name) {
        Category category = new Category();
        category.setId(id);
        category.setName(name);
        return category;
    }

    private CourseRequest request() {
        CourseRequest request = new CourseRequest();
        request.setCategoryId(2L);
        request.setTitle("Updated");
        request.setDescription("Description");
        request.setPrice(new BigDecimal("100.00"));
        request.setLevel("BEGINNER");
        request.setStatus("PUBLISHED");
        return request;
    }
}
