package com.ccnlthd.course_management.service.impl;

import com.ccnlthd.course_management.dto.request.StudentRequest;
import com.ccnlthd.course_management.dto.response.StudentResponse;
import com.ccnlthd.course_management.entity.Student;
import com.ccnlthd.course_management.exception.AppException;
import com.ccnlthd.course_management.exception.ErrorCode;
import com.ccnlthd.course_management.repository.EnrollmentRepository;
import com.ccnlthd.course_management.repository.StudentRepository;
import com.ccnlthd.course_management.service.StudentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class StudentServiceImpl implements StudentService {

    private final StudentRepository studentRepository;
    private final EnrollmentRepository enrollmentRepository;

    @Override
    @Transactional
    public StudentResponse createStudent(StudentRequest request) {
        String email = request.getEmail().trim();
        if (studentRepository.existsByEmailIgnoreCase(email)) {
            throw new AppException(ErrorCode.STUDENT_EMAIL_ALREADY_EXISTS);
        }

        Student student = new Student();
        student.setFullName(request.getFullName().trim());
        student.setEmail(email);
        student.setPhone(request.getPhone());
        return StudentResponse.from(studentRepository.save(student));
    }

    @Override
    @Transactional(readOnly = true)
    public List<StudentResponse> getAllStudents() {
        return studentRepository.findAll().stream()
                .map(StudentResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public StudentResponse getStudentById(Long id) {
        Student student = studentRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.STUDENT_NOT_FOUND));
        return StudentResponse.from(student);
    }

    @Override
    @Transactional
    public StudentResponse updateStudent(Long id, StudentRequest request) {
        Student student = studentRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.STUDENT_NOT_FOUND));
        String email = request.getEmail().trim();
        if (studentRepository.existsByEmailIgnoreCaseAndIdNot(email, id)) {
            throw new AppException(ErrorCode.STUDENT_EMAIL_ALREADY_EXISTS);
        }

        student.setFullName(request.getFullName().trim());
        student.setEmail(email);
        student.setPhone(request.getPhone());
        return StudentResponse.from(studentRepository.save(student));
    }

    @Override
    @Transactional
    public void deleteStudent(Long id) {
        Student student = studentRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.STUDENT_NOT_FOUND));
        if (enrollmentRepository.existsByStudent_Id(id)) {
            throw new AppException(ErrorCode.STUDENT_HAS_ENROLLMENTS);
        }
        studentRepository.delete(student);
    }
}
