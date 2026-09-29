package com.ccnlthd.course_management.dto.response;

import com.ccnlthd.course_management.entity.Student;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StudentResponse {

    private Long id;
    private String fullName;
    private String email;
    private String phone;

    public static StudentResponse from(Student student) {
        return new StudentResponse(
                student.getId(),
                student.getFullName(),
                student.getEmail(),
                student.getPhone()
        );
    }
}
