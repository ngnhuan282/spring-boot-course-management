package com.ccnlthd.course_management;

import com.ccnlthd.course_management.entity.Category;
import com.ccnlthd.course_management.entity.Course;
import com.ccnlthd.course_management.entity.Enrollment;
import com.ccnlthd.course_management.entity.Student;
import com.ccnlthd.course_management.repository.CategoryRepository;
import com.ccnlthd.course_management.repository.CourseRepository;
import com.ccnlthd.course_management.repository.EnrollmentRepository;
import com.ccnlthd.course_management.repository.StudentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class RepositoryIntegrationTests {

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    @Test
    void shouldLoadSeedDataAndQueryRepositories() {
        Category backend = categoryRepository.findByNameIgnoreCase("backend")
                .orElseThrow();
        Student student = studentRepository.findByEmailIgnoreCase("an.nguyen@example.com")
                .orElseThrow();

        List<Course> backendCourses = courseRepository.findByCategory_Id(backend.getId());
        List<Course> publishedCourses = courseRepository.findByStatusIgnoreCase("published");
        List<Course> affordableCourses = courseRepository.findByPriceBetween(
                BigDecimal.ZERO,
                new BigDecimal("500000.00")
        );
        List<Course> jpaCourses = courseRepository.searchByKeyword("JPA");
        List<Enrollment> enrollments = enrollmentRepository.findWithCourseByStudentId(student.getId());

        assertFalse(backendCourses.isEmpty());
        assertFalse(publishedCourses.isEmpty());
        assertFalse(affordableCourses.isEmpty());
        assertFalse(jpaCourses.isEmpty());
        assertFalse(enrollments.isEmpty());
        assertTrue(enrollmentRepository.findByStudent_IdAndCourse_Id(student.getId(), 1L).isPresent());
    }
}
