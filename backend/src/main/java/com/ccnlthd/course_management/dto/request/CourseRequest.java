package com.ccnlthd.course_management.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public class CourseRequest {

    @NotNull(message = "Category id is required")
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
    private String level;

    @NotBlank(message = "Course status is required")
    @Size(max = 50, message = "Course status must not exceed 50 characters")
    private String status;

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
