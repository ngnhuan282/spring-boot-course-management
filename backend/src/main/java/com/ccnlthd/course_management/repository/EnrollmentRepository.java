package com.ccnlthd.course_management.repository;

import com.ccnlthd.course_management.entity.Enrollment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

    List<Enrollment> findByStudent_Id(Long studentId);

    List<Enrollment> findByCourse_Id(Long courseId);

    Optional<Enrollment> findByStudent_IdAndCourse_Id(Long studentId, Long courseId);

    boolean existsByCourse_Id(Long courseId);

    @Query("""
            select e
            from Enrollment e
            join fetch e.course
            where e.student.id = :studentId
            """)
    List<Enrollment> findWithCourseByStudentId(@Param("studentId") Long studentId);
}
