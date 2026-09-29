package com.ccnlthd.course_management;

import com.ccnlthd.course_management.controller.StudentController;
import com.ccnlthd.course_management.dto.request.StudentRequest;
import com.ccnlthd.course_management.dto.response.StudentResponse;
import com.ccnlthd.course_management.entity.Student;
import com.ccnlthd.course_management.exception.AppException;
import com.ccnlthd.course_management.exception.ErrorCode;
import com.ccnlthd.course_management.exception.GlobalExceptionHandler;
import com.ccnlthd.course_management.repository.EnrollmentRepository;
import com.ccnlthd.course_management.repository.StudentRepository;
import com.ccnlthd.course_management.service.StudentService;
import com.ccnlthd.course_management.service.impl.StudentServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StudentCrudTests {

    private StudentRepository studentRepository;
    private EnrollmentRepository enrollmentRepository;
    private StudentServiceImpl service;

    @BeforeEach
    void setUp() {
        studentRepository = mock(StudentRepository.class);
        enrollmentRepository = mock(EnrollmentRepository.class);
        service = new StudentServiceImpl(studentRepository, enrollmentRepository);
    }

    @Test
    void createsStudentWithNormalizedInput() {
        when(studentRepository.save(any(Student.class))).thenAnswer(invocation -> {
            Student student = invocation.getArgument(0);
            student.setId(1L);
            return student;
        });

        StudentResponse response = service.createStudent(request(" An Nguyen ", " an@example.com "));

        assertEquals(1L, response.getId());
        assertEquals("An Nguyen", response.getFullName());
        assertEquals("an@example.com", response.getEmail());
        verify(studentRepository).existsByEmailIgnoreCase("an@example.com");
    }

    @Test
    void rejectsDuplicateEmailOnCreate() {
        when(studentRepository.existsByEmailIgnoreCase("an@example.com")).thenReturn(true);

        AppException exception = assertThrows(AppException.class,
                () -> service.createStudent(request("An Nguyen", " an@example.com ")));

        assertEquals(ErrorCode.STUDENT_EMAIL_ALREADY_EXISTS, exception.getErrorCode());
        verify(studentRepository, never()).save(any(Student.class));
    }

    @Test
    void listsAndReadsStudents() {
        Student student = student();
        when(studentRepository.findAll()).thenReturn(List.of(student));
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));

        assertEquals("An Nguyen", service.getAllStudents().getFirst().getFullName());
        assertEquals("an@example.com", service.getStudentById(1L).getEmail());
    }

    @Test
    void reportsMissingStudentOnReadUpdateAndDelete() {
        when(studentRepository.findById(99L)).thenReturn(Optional.empty());

        assertEquals(ErrorCode.STUDENT_NOT_FOUND,
                assertThrows(AppException.class, () -> service.getStudentById(99L)).getErrorCode());
        assertEquals(ErrorCode.STUDENT_NOT_FOUND,
                assertThrows(AppException.class,
                        () -> service.updateStudent(99L, request("An Nguyen", "an@example.com"))).getErrorCode());
        assertEquals(ErrorCode.STUDENT_NOT_FOUND,
                assertThrows(AppException.class, () -> service.deleteStudent(99L)).getErrorCode());
        verifyNoInteractions(enrollmentRepository);
    }

    @Test
    void updatesStudentWithTheirOwnEmail() {
        Student student = student();
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));
        when(studentRepository.save(student)).thenReturn(student);

        StudentResponse response = service.updateStudent(1L, request(" Binh Nguyen ", " AN@example.com "));

        assertEquals("Binh Nguyen", response.getFullName());
        assertEquals("AN@example.com", response.getEmail());
        verify(studentRepository).existsByEmailIgnoreCaseAndIdNot("AN@example.com", 1L);
        verify(studentRepository).save(student);
    }

    @Test
    void rejectsAnotherStudentsEmailWithoutChangingEntity() {
        Student student = student();
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));
        when(studentRepository.existsByEmailIgnoreCaseAndIdNot("other@example.com", 1L))
                .thenReturn(true);

        AppException exception = assertThrows(AppException.class,
                () -> service.updateStudent(1L, request("Binh Nguyen", "other@example.com")));

        assertEquals(ErrorCode.STUDENT_EMAIL_ALREADY_EXISTS, exception.getErrorCode());
        assertEquals("An Nguyen", student.getFullName());
        verify(studentRepository, never()).save(any(Student.class));
    }

    @Test
    void deletesStudentWithoutEnrollments() {
        Student student = student();
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));

        service.deleteStudent(1L);

        verify(enrollmentRepository).existsByStudent_Id(1L);
        verify(studentRepository).delete(student);
    }

    @Test
    void rejectsDeleteWhenStudentHasEnrollments() {
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student()));
        when(enrollmentRepository.existsByStudent_Id(1L)).thenReturn(true);

        AppException exception = assertThrows(AppException.class,
                () -> service.deleteStudent(1L));

        assertEquals(ErrorCode.STUDENT_HAS_ENROLLMENTS, exception.getErrorCode());
        verify(studentRepository, never()).delete(any(Student.class));
    }

    @Test
    void exposesAllCrudEndpoints() throws Exception {
        StudentService apiService = mock(StudentService.class);
        MockMvc mvc = mvc(apiService);
        StudentResponse response = new StudentResponse(1L, "An Nguyen", "an@example.com", "0901000001");
        when(apiService.createStudent(any(StudentRequest.class))).thenReturn(response);
        when(apiService.getAllStudents()).thenReturn(List.of(response));
        when(apiService.getStudentById(1L)).thenReturn(response);
        when(apiService.updateStudent(any(Long.class), any(StudentRequest.class))).thenReturn(response);
        String body = "{\"fullName\":\"An Nguyen\",\"email\":\"an@example.com\",\"phone\":\"0901000001\"}";

        mvc.perform(post("/api/students").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1));
        mvc.perform(get("/api/students"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value("an@example.com"));
        mvc.perform(get("/api/students/{id}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("An Nguyen"));
        mvc.perform(put("/api/students/{id}", 1L).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value("0901000001"));
        mvc.perform(delete("/api/students/{id}", 1L))
                .andExpect(status().isNoContent());
        verify(apiService).deleteStudent(1L);
    }

    @Test
    void rejectsInvalidRequestBeforeServiceCall() throws Exception {
        StudentService apiService = mock(StudentService.class);
        MockMvc mvc = mvc(apiService);

        mvc.perform(post("/api/students")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"\",\"email\":\"invalid\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(put("/api/students/{id}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"An Nguyen\",\"email\":\"invalid\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        verifyNoInteractions(apiService);
    }

    @Test
    void exposesNotFoundAndConflictResponses() throws Exception {
        StudentService apiService = mock(StudentService.class);
        MockMvc mvc = mvc(apiService);
        when(apiService.getStudentById(99L))
                .thenThrow(new AppException(ErrorCode.STUDENT_NOT_FOUND));
        when(apiService.createStudent(any(StudentRequest.class)))
                .thenThrow(new AppException(ErrorCode.STUDENT_EMAIL_ALREADY_EXISTS));
        doThrow(new AppException(ErrorCode.STUDENT_HAS_ENROLLMENTS))
                .when(apiService).deleteStudent(1L);

        mvc.perform(get("/api/students/{id}", 99L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STUDENT_NOT_FOUND"));
        mvc.perform(post("/api/students")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"An Nguyen\",\"email\":\"an@example.com\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STUDENT_EMAIL_ALREADY_EXISTS"));
        mvc.perform(delete("/api/students/{id}", 1L))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STUDENT_HAS_ENROLLMENTS"));
    }

    private MockMvc mvc(StudentService apiService) {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        return MockMvcBuilders.standaloneSetup(new StudentController(apiService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    private StudentRequest request(String fullName, String email) {
        StudentRequest request = new StudentRequest();
        request.setFullName(fullName);
        request.setEmail(email);
        request.setPhone("0901000001");
        return request;
    }

    private Student student() {
        Student student = new Student();
        student.setId(1L);
        student.setFullName("An Nguyen");
        student.setEmail("an@example.com");
        student.setPhone("0901000001");
        return student;
    }
}
