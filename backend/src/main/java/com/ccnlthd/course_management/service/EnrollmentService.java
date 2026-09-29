package com.ccnlthd.course_management.service;

import com.ccnlthd.course_management.dto.request.EnrollmentRequest;
import com.ccnlthd.course_management.dto.response.EnrollmentResponse;

import java.util.List;

public interface EnrollmentService {

    EnrollmentResponse createEnrollment(EnrollmentRequest request);

    List<EnrollmentResponse> getAllEnrollments();

    EnrollmentResponse getEnrollmentById(Long id);

    EnrollmentResponse updateEnrollment(Long id, EnrollmentRequest request);

    void deleteEnrollment(Long id);
}
