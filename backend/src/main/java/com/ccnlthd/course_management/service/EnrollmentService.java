package com.ccnlthd.course_management.service;

import com.ccnlthd.course_management.dto.request.EnrollmentRequest;
import com.ccnlthd.course_management.dto.response.EnrollmentResponse;

public interface EnrollmentService {

    EnrollmentResponse createEnrollment(EnrollmentRequest request);
}
