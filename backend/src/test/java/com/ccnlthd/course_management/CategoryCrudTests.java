package com.ccnlthd.course_management;

import com.ccnlthd.course_management.controller.CategoryController;
import com.ccnlthd.course_management.dto.request.CategoryRequest;
import com.ccnlthd.course_management.dto.response.CategoryResponse;
import com.ccnlthd.course_management.entity.Category;
import com.ccnlthd.course_management.exception.AppException;
import com.ccnlthd.course_management.exception.ErrorCode;
import com.ccnlthd.course_management.exception.GlobalExceptionHandler;
import com.ccnlthd.course_management.repository.CategoryRepository;
import com.ccnlthd.course_management.repository.CourseRepository;
import com.ccnlthd.course_management.service.CategoryService;
import com.ccnlthd.course_management.service.impl.CategoryServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CategoryCrudTests {

    private CategoryRepository categoryRepository;
    private CourseRepository courseRepository;
    private CategoryServiceImpl service;

    @BeforeEach
    void setUp() {
        categoryRepository = mock(CategoryRepository.class);
        courseRepository = mock(CourseRepository.class);
        service = new CategoryServiceImpl(categoryRepository, courseRepository);
    }

    @Test
    void createsCategoryWithTrimmedName() {
        CategoryRequest request = request("  Backend  ");
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> {
            Category saved = invocation.getArgument(0);
            saved.setId(1L);
            return saved;
        });

        CategoryResponse response = service.createCategory(request);

        assertEquals(1L, response.getId());
        assertEquals("Backend", response.getName());
        verify(categoryRepository).existsByNameIgnoreCase("Backend");
    }

    @Test
    void rejectsDuplicateNameOnCreate() {
        when(categoryRepository.existsByNameIgnoreCase("Backend")).thenReturn(true);

        AppException exception = assertThrows(AppException.class,
                () -> service.createCategory(request(" Backend ")));

        assertEquals(ErrorCode.CATEGORY_ALREADY_EXISTS, exception.getErrorCode());
        verify(categoryRepository, never()).save(any(Category.class));
    }

    @Test
    void listsAndReadsCategories() {
        Category category = category(1L, "Backend");
        when(categoryRepository.findAll()).thenReturn(List.of(category));
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));

        assertEquals("Backend", service.getAllCategories().getFirst().getName());
        assertEquals("Description", service.getCategoryById(1L).getDescription());
    }

    @Test
    void reportsMissingCategoryOnReadAndUpdate() {
        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

        assertEquals(ErrorCode.CATEGORY_NOT_FOUND,
                assertThrows(AppException.class, () -> service.getCategoryById(99L)).getErrorCode());
        assertEquals(ErrorCode.CATEGORY_NOT_FOUND,
                assertThrows(AppException.class,
                        () -> service.updateCategory(99L, request("Backend"))).getErrorCode());
        verify(categoryRepository, never()).save(any(Category.class));
    }

    @Test
    void updatesOwnNameAndDescription() {
        Category category = category(1L, "Backend");
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(categoryRepository.save(category)).thenReturn(category);

        CategoryResponse response = service.updateCategory(1L, request(" backend "));

        assertEquals("backend", response.getName());
        assertEquals("New description", response.getDescription());
        verify(categoryRepository).existsByNameIgnoreCaseAndIdNot("backend", 1L);
        verify(categoryRepository).save(category);
    }

    @Test
    void rejectsAnotherCategoryNameOnUpdateWithoutChangingEntity() {
        Category category = category(1L, "Backend");
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(categoryRepository.existsByNameIgnoreCaseAndIdNot("Database", 1L))
                .thenReturn(true);

        AppException exception = assertThrows(AppException.class,
                () -> service.updateCategory(1L, request(" Database ")));

        assertEquals(ErrorCode.CATEGORY_ALREADY_EXISTS, exception.getErrorCode());
        assertEquals("Backend", category.getName());
        verify(categoryRepository, never()).save(any(Category.class));
    }

    @Test
    void deletesCategoryWithoutCourses() {
        Category category = category(1L, "Backend");
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));

        service.deleteCategory(1L);

        verify(courseRepository).existsByCategory_Id(1L);
        verify(categoryRepository).delete(category);
    }

    @Test
    void rejectsDeleteWhenCategoryIsMissingOrHasCourses() {
        when(categoryRepository.findById(1L)).thenReturn(Optional.empty());
        assertEquals(ErrorCode.CATEGORY_NOT_FOUND,
                assertThrows(AppException.class, () -> service.deleteCategory(1L)).getErrorCode());
        verifyNoInteractions(courseRepository);

        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category(1L, "Backend")));
        when(courseRepository.existsByCategory_Id(1L)).thenReturn(true);
        assertEquals(ErrorCode.CATEGORY_HAS_COURSES,
                assertThrows(AppException.class, () -> service.deleteCategory(1L)).getErrorCode());
        verify(categoryRepository, never()).delete(any(Category.class));
    }

    @Test
    void exposesAllCrudEndpoints() throws Exception {
        CategoryService apiService = mock(CategoryService.class);
        MockMvc mvc = mvc(apiService);
        CategoryResponse response = new CategoryResponse(1L, "Backend", "Description");
        when(apiService.createCategory(any(CategoryRequest.class))).thenReturn(response);
        when(apiService.getAllCategories()).thenReturn(List.of(response));
        when(apiService.getCategoryById(1L)).thenReturn(response);
        when(apiService.updateCategory(any(Long.class), any(CategoryRequest.class)))
                .thenReturn(response);

        mvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Backend\",\"description\":\"Description\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1));
        mvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Backend"));
        mvc.perform(get("/api/categories/{id}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Description"));
        mvc.perform(put("/api/categories/{id}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Backend\",\"description\":\"Description\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Backend"));
        mvc.perform(delete("/api/categories/{id}", 1L))
                .andExpect(status().isNoContent());
        verify(apiService).deleteCategory(1L);
    }

    @Test
    void invalidRequestsDoNotReachService() throws Exception {
        CategoryService apiService = mock(CategoryService.class);
        MockMvc mvc = mvc(apiService);

        mvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(put("/api/categories/{id}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        verifyNoInteractions(apiService);
    }

    @Test
    void exposesNotFoundAndConflictResponses() throws Exception {
        CategoryService apiService = mock(CategoryService.class);
        MockMvc mvc = mvc(apiService);
        when(apiService.getCategoryById(99L))
                .thenThrow(new AppException(ErrorCode.CATEGORY_NOT_FOUND));
        when(apiService.createCategory(any(CategoryRequest.class)))
                .thenThrow(new AppException(ErrorCode.CATEGORY_ALREADY_EXISTS));
        doThrow(new AppException(ErrorCode.CATEGORY_HAS_COURSES))
                .when(apiService).deleteCategory(1L);

        mvc.perform(get("/api/categories/{id}", 99L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"));
        mvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Backend\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATEGORY_ALREADY_EXISTS"));
        mvc.perform(delete("/api/categories/{id}", 1L))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATEGORY_HAS_COURSES"));
    }

    private MockMvc mvc(CategoryService apiService) {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        return MockMvcBuilders.standaloneSetup(new CategoryController(apiService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    private CategoryRequest request(String name) {
        CategoryRequest request = new CategoryRequest();
        request.setName(name);
        request.setDescription("New description");
        return request;
    }

    private Category category(Long id, String name) {
        Category category = new Category();
        category.setId(id);
        category.setName(name);
        category.setDescription("Description");
        return category;
    }
}
