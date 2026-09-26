package com.ccnlthd.course_management;

import com.ccnlthd.course_management.dto.request.CourseRequest;
import com.ccnlthd.course_management.dto.response.CourseResponse;
import com.ccnlthd.course_management.exception.AppException;
import com.ccnlthd.course_management.exception.ErrorCode;
import com.ccnlthd.course_management.service.CourseService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class CourseCrudIntegrationTests {

    @Autowired
    private CourseService courseService;

    @Test
    @Transactional
    void createsUpdatesListsAndDeletesCourse() {
        CourseRequest request = new CourseRequest();
        request.setTitle("CRUD integration course");
        request.setPrice(new BigDecimal("100000.00"));
        request.setLevel("BEGINNER");
        request.setStatus("DRAFT");

        CourseResponse created = courseService.createCourse(request);
        Long id = created.getId();
        assertEquals("CRUD integration course", courseService.getCourseById(id).getTitle());
        assertTrue(courseService.getAllCourses().stream().anyMatch(course -> id.equals(course.getId())));

        request.setTitle("Updated integration course");
        request.setPrice(new BigDecimal("200000.00"));
        request.setLevel("INTERMEDIATE");
        request.setStatus("PUBLISHED");
        CourseResponse updated = courseService.updateCourse(id, request);
        assertEquals("Updated integration course", updated.getTitle());
        assertEquals(new BigDecimal("200000.00"), updated.getPrice());

        courseService.deleteCourse(id);
        AppException exception = assertThrows(AppException.class, () -> courseService.getCourseById(id));
        assertEquals(ErrorCode.COURSE_NOT_FOUND, exception.getErrorCode());
    }
}
