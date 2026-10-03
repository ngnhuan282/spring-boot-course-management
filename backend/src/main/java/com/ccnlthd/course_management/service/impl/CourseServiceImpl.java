package com.ccnlthd.course_management.service.impl;

import com.ccnlthd.course_management.dto.request.CourseRequest;
import com.ccnlthd.course_management.dto.response.CourseResponse;
import com.ccnlthd.course_management.entity.Course;
import com.ccnlthd.course_management.exception.AppException;
import com.ccnlthd.course_management.exception.ErrorCode;
import com.ccnlthd.course_management.repository.CourseRepository;
import com.ccnlthd.course_management.service.CourseService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CourseServiceImpl implements CourseService {

    private final CourseRepository courseRepository;

    @Override
    public CourseResponse createCourse(CourseRequest request) {
        Course course = new Course();
        course.setTitle(request.getTitle());
        course.setDescription(request.getDescription());
        course.setPrice(request.getPrice());
        course.setLevel(request.getLevel());
        course.setStatus(request.getStatus());

        Course savedCourse = courseRepository.save(course);

        return new CourseResponse(
                savedCourse.getId(),
                savedCourse.getTitle(),
                savedCourse.getDescription(),
                savedCourse.getPrice(),
                savedCourse.getLevel(),
                savedCourse.getStatus()
        );
    }

    @Override
    public CourseResponse getCourseById(Long id) {
        Course course = courseRepository.findById(id).orElse(null);

        if (course == null) {
            throw new AppException(ErrorCode.COURSE_NOT_FOUND);
        }

        return new CourseResponse(
                course.getId(),
                course.getTitle(),
                course.getDescription(),
                course.getPrice(),
                course.getLevel(),
                course.getStatus()
        );
    }

    @Override
    public List<CourseResponse> getAllCourses() {
        List<Course> courses = courseRepository.findAll();
        List<CourseResponse> responses = new ArrayList<>();

        for (Course course : courses) {
            CourseResponse response = new CourseResponse(
                    course.getId(),
                    course.getTitle(),
                    course.getDescription(),
                    course.getPrice(),
                    course.getLevel(),
                    course.getStatus()
            );
            responses.add(response);
        }

        return responses;
    }

    @Override
    public CourseResponse updateCourse(Long id, CourseRequest request) {
        Course course = courseRepository.findById(id).orElse(null);

        if (course == null) {
            throw new AppException(ErrorCode.COURSE_NOT_FOUND);
        }

        course.setTitle(request.getTitle());
        course.setDescription(request.getDescription());
        course.setPrice(request.getPrice());
        course.setLevel(request.getLevel());
        course.setStatus(request.getStatus());

        Course savedCourse = courseRepository.save(course);

        return new CourseResponse(
                savedCourse.getId(),
                savedCourse.getTitle(),
                savedCourse.getDescription(),
                savedCourse.getPrice(),
                savedCourse.getLevel(),
                savedCourse.getStatus()
        );
    }

    @Override
    public void deleteCourse(Long id) {
        Course course = courseRepository.findById(id).orElse(null);

        if (course == null) {
            throw new AppException(ErrorCode.COURSE_NOT_FOUND);
        }

        courseRepository.delete(course);
    }
}
