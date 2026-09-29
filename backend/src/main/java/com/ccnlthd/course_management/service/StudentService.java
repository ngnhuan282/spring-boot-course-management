package com.ccnlthd.course_management.service;

import com.ccnlthd.course_management.dto.request.StudentRequest;
import com.ccnlthd.course_management.dto.response.StudentResponse;

import java.util.List;

public interface StudentService {

    StudentResponse createStudent(StudentRequest request);

    List<StudentResponse> getAllStudents();

    StudentResponse getStudentById(Long id);

    StudentResponse updateStudent(Long id, StudentRequest request);

    void deleteStudent(Long id);
}
