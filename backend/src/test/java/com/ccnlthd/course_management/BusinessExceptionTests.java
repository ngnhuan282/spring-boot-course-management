package com.ccnlthd.course_management;

import com.ccnlthd.course_management.dto.request.CourseRequest;
import com.ccnlthd.course_management.dto.request.EnrollmentRequest;
import com.ccnlthd.course_management.entity.Category;
import com.ccnlthd.course_management.entity.Course;
import com.ccnlthd.course_management.entity.Enrollment;
import com.ccnlthd.course_management.entity.Student;
import com.ccnlthd.course_management.exception.AppException;
import com.ccnlthd.course_management.exception.ErrorCode;
import com.ccnlthd.course_management.repository.CategoryRepository;
import com.ccnlthd.course_management.repository.CourseRepository;
import com.ccnlthd.course_management.repository.EnrollmentRepository;
import com.ccnlthd.course_management.repository.StudentRepository;
import com.ccnlthd.course_management.service.impl.CourseServiceImpl;
import com.ccnlthd.course_management.service.impl.EnrollmentServiceImpl;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BusinessExceptionTests {

    @Test
    void shouldThrowCategoryNotFoundWhenCreatingCourseWithMissingCategory() {
        CourseRepository courseRepository = mock(CourseRepository.class);
        CategoryRepository categoryRepository = mock(CategoryRepository.class);
        CourseServiceImpl courseService = new CourseServiceImpl(courseRepository, categoryRepository);

        CourseRequest request = validCourseRequest();
        when(categoryRepository.findById(404L)).thenReturn(Optional.empty());

        AppException exception = assertThrows(
                AppException.class,
                () -> courseService.createCourse(request)
        );

        assertEquals(ErrorCode.CATEGORY_NOT_FOUND, exception.getErrorCode());
        verify(categoryRepository).findById(404L);
        verifyNoInteractions(courseRepository);
    }

    @Test
    void shouldThrowCourseNotFoundWhenCourseDoesNotExist() {
        CourseRepository courseRepository = mock(CourseRepository.class);
        CategoryRepository categoryRepository = mock(CategoryRepository.class);
        CourseServiceImpl courseService = new CourseServiceImpl(courseRepository, categoryRepository);

        when(courseRepository.findById(99L)).thenReturn(Optional.empty());

        AppException exception = assertThrows(
                AppException.class,
                () -> courseService.getCourseById(99L)
        );

        assertEquals(ErrorCode.COURSE_NOT_FOUND, exception.getErrorCode());
        verify(courseRepository).findById(99L);
        verifyNoInteractions(categoryRepository);
    }

    @Test
    void shouldThrowEnrollmentAlreadyExistsForDuplicateEnrollment() {
        EnrollmentRepository enrollmentRepository = mock(EnrollmentRepository.class);
        StudentRepository studentRepository = mock(StudentRepository.class);
        CourseRepository courseRepository = mock(CourseRepository.class);
        EnrollmentServiceImpl enrollmentService = new EnrollmentServiceImpl(
                enrollmentRepository,
                studentRepository,
                courseRepository
        );

        EnrollmentRequest request = validEnrollmentRequest();
        Student student = student(1L);
        Course course = course(2L);

        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));
        when(courseRepository.findById(2L)).thenReturn(Optional.of(course));
        when(enrollmentRepository.findByStudent_IdAndCourse_Id(1L, 2L))
                .thenReturn(Optional.of(new Enrollment()));

        AppException exception = assertThrows(
                AppException.class,
                () -> enrollmentService.createEnrollment(request)
        );

        assertEquals(ErrorCode.ENROLLMENT_ALREADY_EXISTS, exception.getErrorCode());
    }

    private CourseRequest validCourseRequest() {
        CourseRequest request = new CourseRequest();
        request.setCategoryId(404L);
        request.setTitle("Spring Boot Fundamentals");
        request.setDescription("Build REST APIs with Spring Boot.");
        request.setPrice(new BigDecimal("499000.00"));
        request.setLevel("BEGINNER");
        request.setStatus("PUBLISHED");
        return request;
    }

    private EnrollmentRequest validEnrollmentRequest() {
        EnrollmentRequest request = new EnrollmentRequest();
        request.setStudentId(1L);
        request.setCourseId(2L);
        request.setStatus("ACTIVE");
        return request;
    }

    private Student student(Long id) {
        Student student = new Student();
        student.setId(id);
        return student;
    }

    private Course course(Long id) {
        Course course = new Course();
        course.setId(id);
        course.setCategory(new Category());
        return course;
    }
}
