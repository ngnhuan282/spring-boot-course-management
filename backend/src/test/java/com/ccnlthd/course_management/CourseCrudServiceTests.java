package com.ccnlthd.course_management;

import com.ccnlthd.course_management.dto.request.CourseRequest;
import com.ccnlthd.course_management.dto.response.CourseResponse;
import com.ccnlthd.course_management.entity.Course;
import com.ccnlthd.course_management.exception.AppException;
import com.ccnlthd.course_management.exception.ErrorCode;
import com.ccnlthd.course_management.repository.CourseRepository;
import com.ccnlthd.course_management.service.impl.CourseServiceImpl;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CourseCrudServiceTests {

    private final CourseRepository courseRepository = mock(CourseRepository.class);
    private final CourseServiceImpl service = new CourseServiceImpl(courseRepository);

    @Test
    void listsCoursesAsResponses() {
        Course course = course(10L);
        course.setTitle("Spring Boot");
        when(courseRepository.findAllByOrderByIdAsc()).thenReturn(List.of(course));

        List<CourseResponse> responses = service.getAllCourses();

        assertEquals(1, responses.size());
        assertEquals(10L, responses.getFirst().getId());
        assertEquals("Spring Boot", responses.getFirst().getTitle());
    }

    @Test
    void updatesExistingCourse() {
        Course course = course(10L);
        CourseRequest request = request();
        when(courseRepository.findById(10L)).thenReturn(Optional.of(course));
        when(courseRepository.save(course)).thenReturn(course);

        CourseResponse response = service.updateCourse(10L, request);

        assertEquals(10L, response.getId());
        assertEquals(request.getTitle(), response.getTitle());
        assertEquals(request.getDescription(), response.getDescription());
        assertEquals(request.getPrice(), response.getPrice());
        assertEquals(request.getLevel(), response.getLevel());
        assertEquals(request.getStatus(), response.getStatus());
        verify(courseRepository).save(course);
    }

    @Test
    void rejectsUpdateWhenCourseIsMissing() {
        CourseRequest request = request();
        AppException missingCourse = assertThrows(
                AppException.class, () -> service.updateCourse(99L, request)
        );
        assertEquals(ErrorCode.COURSE_NOT_FOUND, missingCourse.getErrorCode());

        verify(courseRepository, never()).save(any(Course.class));
    }

    @Test
    void rejectsDeletingMissingCourse() {
        AppException exception = assertThrows(AppException.class, () -> service.deleteCourse(10L));
        assertEquals(ErrorCode.COURSE_NOT_FOUND, exception.getErrorCode());
        verify(courseRepository, never()).delete(any(Course.class));
    }

    @Test
    void deletesCourse() {
        Course course = course(10L);
        when(courseRepository.findById(10L)).thenReturn(Optional.of(course));

        service.deleteCourse(10L);

        verify(courseRepository).delete(course);
    }

    private CourseRequest request() {
        CourseRequest request = new CourseRequest();
        request.setTitle("Updated course");
        request.setDescription("Updated description");
        request.setPrice(new BigDecimal("299000.00"));
        request.setLevel("INTERMEDIATE");
        request.setStatus("PUBLISHED");
        return request;
    }

    private Course course(Long id) {
        Course course = new Course();
        course.setId(id);
        return course;
    }
}
