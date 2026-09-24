package com.ccnlthd.course_management.dto.response;

import com.ccnlthd.course_management.entity.Course;

import java.math.BigDecimal;

public class CourseResponse {

    private Long id;
    private Long categoryId;
    private String categoryName;
    private String title;
    private String description;
    private BigDecimal price;
    private String level;
    private String status;

    public CourseResponse() {
    }

    public CourseResponse(
            Long id,
            Long categoryId,
            String categoryName,
            String title,
            String description,
            BigDecimal price,
            String level,
            String status
    ) {
        this.id = id;
        this.categoryId = categoryId;
        this.categoryName = categoryName;
        this.title = title;
        this.description = description;
        this.price = price;
        this.level = level;
        this.status = status;
    }

    public static CourseResponse from(Course course) {
        return new CourseResponse(
                course.getId(),
                course.getCategory().getId(),
                course.getCategory().getName(),
                course.getTitle(),
                course.getDescription(),
                course.getPrice(),
                course.getLevel(),
                course.getStatus()
        );
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
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
