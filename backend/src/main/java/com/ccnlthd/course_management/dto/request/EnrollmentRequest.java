package com.ccnlthd.course_management.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EnrollmentRequest {

    @NotNull(message = "Student id is required")
    @Positive(message = "Student id must be positive")
    @Schema(minimum = "1", example = "2", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long studentId;

    @NotNull(message = "Course id is required")
    @Positive(message = "Course id must be positive")
    @Schema(minimum = "1", example = "3", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long courseId;

    @NotBlank(message = "Enrollment status is required")
    @Size(max = 50, message = "Enrollment status must not exceed 50 characters")
    @Pattern(regexp = "ACTIVE|COMPLETED", message = "Enrollment status must be ACTIVE or COMPLETED")
    @Schema(allowableValues = {"ACTIVE", "COMPLETED"}, example = "ACTIVE",
            description = "Exact uppercase value; surrounding whitespace is not accepted",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String status;

}
