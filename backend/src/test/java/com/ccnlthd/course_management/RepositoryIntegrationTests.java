package com.ccnlthd.course_management;

import com.ccnlthd.course_management.entity.Course;
import com.ccnlthd.course_management.repository.CourseRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;

@SpringBootTest
class RepositoryIntegrationTests {

    @Autowired
    private CourseRepository courseRepository;

    @Test
    void shouldLoadCourseSeedDataAndQueryRepository() {
        List<Course> publishedCourses = courseRepository.findByStatusIgnoreCase("published");
        List<Course> affordableCourses = courseRepository.findByPriceBetween(
                BigDecimal.ZERO,
                new BigDecimal("500000.00")
        );
        List<Course> jpaCourses = courseRepository.searchByKeyword("JPA");

        assertFalse(publishedCourses.isEmpty());
        assertFalse(affordableCourses.isEmpty());
        assertFalse(jpaCourses.isEmpty());
    }
}
