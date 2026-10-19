package com.ccnlthd.course_management.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Setter
@Getter
public class CourseRequest {

    @NotNull(message = "Category id is required")
    @Positive(message = "Category id must be positive")
    @Schema(minimum = "1", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long categoryId;

    @NotBlank(message = "Course title is required")
    @Size(max = 150, message = "Course title must not exceed 150 characters")
    private String title;

    @Size(max = 2000, message = "Course description must not exceed 2000 characters")
    private String description;

    @NotNull(message = "Course price is required")
    @PositiveOrZero(message = "Course price must be zero or positive")
    private BigDecimal price;

    @NotBlank(message = "Course level is required")
    @Size(max = 50, message = "Course level must not exceed 50 characters")
    @Pattern(regexp = "BEGINNER|INTERMEDIATE|ADVANCED",
            message = "Course level must be BEGINNER, INTERMEDIATE or ADVANCED")
    @Schema(allowableValues = {"BEGINNER", "INTERMEDIATE", "ADVANCED"})
    private String level;

    @NotBlank(message = "Course status is required")
    @Size(max = 50, message = "Course status must not exceed 50 characters")
    @Pattern(regexp = "DRAFT|PUBLISHED|ARCHIVED",
            message = "Course status must be DRAFT, PUBLISHED or ARCHIVED")
    @Schema(allowableValues = {"DRAFT", "PUBLISHED", "ARCHIVED"})
    private String status;

}
