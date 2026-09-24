package com.ccnlthd.course_management.repository;

import com.ccnlthd.course_management.entity.Course;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface CourseRepository extends JpaRepository<Course, Long> {

    List<Course> findByCategory_Id(Long categoryId);

    List<Course> findByStatusIgnoreCase(String status);

    List<Course> findByPriceBetween(BigDecimal minPrice, BigDecimal maxPrice);

    @Query("""
            select c
            from Course c
            where lower(c.category.name) = lower(:categoryName)
            """)
    List<Course> findByCategoryName(@Param("categoryName") String categoryName);

    @Query("""
            select c
            from Course c
            where lower(c.title) like lower(concat('%', :keyword, '%'))
               or lower(c.description) like lower(concat('%', :keyword, '%'))
            """)
    List<Course> searchByKeyword(@Param("keyword") String keyword);
}
