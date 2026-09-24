package com.ccnlthd.course_management.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class EnrollmentRequest {

    @NotNull(message = "Student id is required")
    private Long studentId;

    @NotNull(message = "Course id is required")
    private Long courseId;

    @NotBlank(message = "Enrollment status is required")
    @Size(max = 50, message = "Enrollment status must not exceed 50 characters")
    private String status;

    public Long getStudentId() {
        return studentId;
    }

    public void setStudentId(Long studentId) {
        this.studentId = studentId;
    }

    public Long getCourseId() {
        return courseId;
    }

    public void setCourseId(Long courseId) {
        this.courseId = courseId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
