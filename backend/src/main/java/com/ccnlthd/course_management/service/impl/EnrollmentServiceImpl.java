package com.ccnlthd.course_management.service.impl;

import com.ccnlthd.course_management.dto.request.EnrollmentRequest;
import com.ccnlthd.course_management.dto.response.EnrollmentResponse;
import com.ccnlthd.course_management.entity.Course;
import com.ccnlthd.course_management.entity.Enrollment;
import com.ccnlthd.course_management.entity.Student;
import com.ccnlthd.course_management.exception.AppException;
import com.ccnlthd.course_management.exception.ErrorCode;
import com.ccnlthd.course_management.repository.CourseRepository;
import com.ccnlthd.course_management.repository.EnrollmentRepository;
import com.ccnlthd.course_management.repository.StudentRepository;
import com.ccnlthd.course_management.service.EnrollmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EnrollmentServiceImpl implements EnrollmentService {

    private final EnrollmentRepository enrollmentRepository;
    private final StudentRepository studentRepository;
    private final CourseRepository courseRepository;

    @Override
    @Transactional
    public EnrollmentResponse createEnrollment(EnrollmentRequest request) {
        Student student = studentRepository.findById(request.getStudentId())
                .orElseThrow(() -> new AppException(ErrorCode.STUDENT_NOT_FOUND));
        Course course = courseRepository.findById(request.getCourseId())
                .orElseThrow(() -> new AppException(ErrorCode.COURSE_NOT_FOUND));

        enrollmentRepository
                .findByStudent_IdAndCourse_Id(student.getId(), course.getId())
                .ifPresent(enrollment -> {
                    throw new AppException(ErrorCode.ENROLLMENT_ALREADY_EXISTS);
                });

        Enrollment enrollment = new Enrollment();
        enrollment.setStudent(student);
        enrollment.setCourse(course);
        enrollment.setStatus(request.getStatus());

        return EnrollmentResponse.from(enrollmentRepository.save(enrollment));
    }

    @Override
    @Transactional(readOnly = true)
    public List<EnrollmentResponse> getAllEnrollments() {
        return enrollmentRepository.findAll().stream()
                .map(EnrollmentResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public EnrollmentResponse getEnrollmentById(Long id) {
        Enrollment enrollment = enrollmentRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.ENROLLMENT_NOT_FOUND));
        return EnrollmentResponse.from(enrollment);
    }

    @Override
    @Transactional
    public EnrollmentResponse updateEnrollment(Long id, EnrollmentRequest request) {
        Enrollment enrollment = enrollmentRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.ENROLLMENT_NOT_FOUND));
        Student student = studentRepository.findById(request.getStudentId())
                .orElseThrow(() -> new AppException(ErrorCode.STUDENT_NOT_FOUND));
        Course course = courseRepository.findById(request.getCourseId())
                .orElseThrow(() -> new AppException(ErrorCode.COURSE_NOT_FOUND));

        enrollmentRepository.findByStudent_IdAndCourse_Id(student.getId(), course.getId())
                .filter(existing -> !id.equals(existing.getId()))
                .ifPresent(existing -> {
                    throw new AppException(ErrorCode.ENROLLMENT_ALREADY_EXISTS);
                });

        enrollment.setStudent(student);
        enrollment.setCourse(course);
        enrollment.setStatus(request.getStatus());
        return EnrollmentResponse.from(enrollmentRepository.save(enrollment));
    }

    @Override
    @Transactional
    public void deleteEnrollment(Long id) {
        Enrollment enrollment = enrollmentRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.ENROLLMENT_NOT_FOUND));
        enrollmentRepository.delete(enrollment);
    }
}
