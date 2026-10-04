package com.ccnlthd.course_management;

import com.ccnlthd.course_management.controller.EnrollmentController;
import com.ccnlthd.course_management.dto.request.EnrollmentRequest;
import com.ccnlthd.course_management.dto.response.EnrollmentResponse;
import com.ccnlthd.course_management.entity.Course;
import com.ccnlthd.course_management.entity.Enrollment;
import com.ccnlthd.course_management.entity.Student;
import com.ccnlthd.course_management.exception.AppException;
import com.ccnlthd.course_management.exception.ErrorCode;
import com.ccnlthd.course_management.exception.GlobalExceptionHandler;
import com.ccnlthd.course_management.repository.CourseRepository;
import com.ccnlthd.course_management.repository.EnrollmentRepository;
import com.ccnlthd.course_management.repository.StudentRepository;
import com.ccnlthd.course_management.service.EnrollmentService;
import com.ccnlthd.course_management.service.impl.EnrollmentServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.LocalDateTime;
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

class EnrollmentCrudTests {

    private EnrollmentRepository enrollmentRepository;
    private StudentRepository studentRepository;
    private CourseRepository courseRepository;
    private EnrollmentServiceImpl service;

    @BeforeEach
    void setUp() {
        enrollmentRepository = mock(EnrollmentRepository.class);
        studentRepository = mock(StudentRepository.class);
        courseRepository = mock(CourseRepository.class);
        service = new EnrollmentServiceImpl(enrollmentRepository, studentRepository, courseRepository);
    }

    @Test
    void listsAndReadsEnrollments() {
        Enrollment enrollment = enrollment(1L, 2L, 3L);
        when(enrollmentRepository.findAll()).thenReturn(List.of(enrollment));
        when(enrollmentRepository.findById(1L)).thenReturn(Optional.of(enrollment));

        assertEquals(2L, service.getAllEnrollments().getFirst().getStudentId());
        assertEquals(3L, service.getEnrollmentById(1L).getCourseId());
    }

    @Test
    void createsEnrollmentWithExistingStudentAndCourse() {
        Student student = student(2L);
        Course course = course(3L);
        when(studentRepository.findById(2L)).thenReturn(Optional.of(student));
        when(courseRepository.findById(3L)).thenReturn(Optional.of(course));
        when(enrollmentRepository.findByStudent_IdAndCourse_Id(2L, 3L)).thenReturn(Optional.empty());
        when(enrollmentRepository.save(any(Enrollment.class))).thenAnswer(invocation -> {
            Enrollment savedEnrollment = invocation.getArgument(0);
            savedEnrollment.setId(7L);
            return savedEnrollment;
        });

        EnrollmentResponse response = service.createEnrollment(request(2L, 3L));

        ArgumentCaptor<Enrollment> savedEnrollment = ArgumentCaptor.forClass(Enrollment.class);
        verify(enrollmentRepository).save(savedEnrollment.capture());
        assertEquals(7L, response.getId());
        assertEquals(2L, response.getStudentId());
        assertEquals(3L, response.getCourseId());
        assertEquals("ACTIVE", response.getStatus());
        assertEquals(2L, savedEnrollment.getValue().getStudent().getId());
        assertEquals(3L, savedEnrollment.getValue().getCourse().getId());
    }

    @Test
    void reportsMissingStudentWhenCreatingEnrollment() {
        when(studentRepository.findById(2L)).thenReturn(Optional.empty());

        AppException exception = assertThrows(AppException.class,
                () -> service.createEnrollment(request(2L, 3L)));

        assertEquals(ErrorCode.STUDENT_NOT_FOUND, exception.getErrorCode());
        verifyNoInteractions(courseRepository);
        verifyNoInteractions(enrollmentRepository);
    }

    @Test
    void reportsMissingCourseWhenCreatingEnrollment() {
        when(studentRepository.findById(2L)).thenReturn(Optional.of(student(2L)));
        when(courseRepository.findById(3L)).thenReturn(Optional.empty());

        AppException exception = assertThrows(AppException.class,
                () -> service.createEnrollment(request(2L, 3L)));

        assertEquals(ErrorCode.COURSE_NOT_FOUND, exception.getErrorCode());
        verifyNoInteractions(enrollmentRepository);
    }

    @Test
    void reportsMissingEnrollmentOnReadUpdateAndDelete() {
        when(enrollmentRepository.findById(99L)).thenReturn(Optional.empty());

        assertEquals(ErrorCode.ENROLLMENT_NOT_FOUND,
                assertThrows(AppException.class, () -> service.getEnrollmentById(99L)).getErrorCode());
        assertEquals(ErrorCode.ENROLLMENT_NOT_FOUND,
                assertThrows(AppException.class,
                        () -> service.updateEnrollment(99L, request(2L, 3L))).getErrorCode());
        assertEquals(ErrorCode.ENROLLMENT_NOT_FOUND,
                assertThrows(AppException.class, () -> service.deleteEnrollment(99L)).getErrorCode());
        verifyNoInteractions(studentRepository, courseRepository);
    }

    @Test
    void updatesExistingEnrollmentWithItsOwnStudentCoursePair() {
        Enrollment enrollment = enrollment(1L, 2L, 3L);
        when(enrollmentRepository.findById(1L)).thenReturn(Optional.of(enrollment));
        when(studentRepository.findById(2L)).thenReturn(Optional.of(enrollment.getStudent()));
        when(courseRepository.findById(3L)).thenReturn(Optional.of(enrollment.getCourse()));
        when(enrollmentRepository.findByStudent_IdAndCourse_Id(2L, 3L))
                .thenReturn(Optional.of(enrollment));
        when(enrollmentRepository.save(enrollment)).thenReturn(enrollment);
        EnrollmentRequest request = request(2L, 3L);
        request.setStatus("COMPLETED");

        EnrollmentResponse response = service.updateEnrollment(1L, request);

        assertEquals("COMPLETED", response.getStatus());
        assertEquals(LocalDateTime.of(2026, 9, 29, 10, 0), response.getEnrolledAt());
        verify(enrollmentRepository).save(enrollment);
    }

    @Test
    void updatesStudentAndCourseWhenPairIsUnused() {
        Enrollment enrollment = enrollment(1L, 2L, 3L);
        Student newStudent = student(4L);
        Course newCourse = course(5L);
        when(enrollmentRepository.findById(1L)).thenReturn(Optional.of(enrollment));
        when(studentRepository.findById(4L)).thenReturn(Optional.of(newStudent));
        when(courseRepository.findById(5L)).thenReturn(Optional.of(newCourse));
        when(enrollmentRepository.save(enrollment)).thenReturn(enrollment);

        EnrollmentResponse response = service.updateEnrollment(1L, request(4L, 5L));

        assertEquals(4L, response.getStudentId());
        assertEquals(5L, response.getCourseId());
        verify(enrollmentRepository).save(enrollment);
    }

    @Test
    void rejectsDuplicatePairWithoutChangingEnrollment() {
        Enrollment enrollment = enrollment(1L, 2L, 3L);
        when(enrollmentRepository.findById(1L)).thenReturn(Optional.of(enrollment));
        when(studentRepository.findById(4L)).thenReturn(Optional.of(student(4L)));
        when(courseRepository.findById(5L)).thenReturn(Optional.of(course(5L)));
        when(enrollmentRepository.findByStudent_IdAndCourse_Id(4L, 5L))
                .thenReturn(Optional.of(enrollment(6L, 4L, 5L)));

        AppException exception = assertThrows(AppException.class,
                () -> service.updateEnrollment(1L, request(4L, 5L)));

        assertEquals(ErrorCode.ENROLLMENT_ALREADY_EXISTS, exception.getErrorCode());
        assertEquals(2L, enrollment.getStudent().getId());
        assertEquals(3L, enrollment.getCourse().getId());
        verify(enrollmentRepository, never()).save(any(Enrollment.class));
    }

    @Test
    void reportsMissingStudentOrCourseBeforeChangingEnrollment() {
        Enrollment enrollment = enrollment(1L, 2L, 3L);
        when(enrollmentRepository.findById(1L)).thenReturn(Optional.of(enrollment));
        when(studentRepository.findById(4L)).thenReturn(Optional.empty());
        assertEquals(ErrorCode.STUDENT_NOT_FOUND,
                assertThrows(AppException.class,
                        () -> service.updateEnrollment(1L, request(4L, 5L))).getErrorCode());
        verifyNoInteractions(courseRepository);

        when(studentRepository.findById(4L)).thenReturn(Optional.of(student(4L)));
        when(courseRepository.findById(5L)).thenReturn(Optional.empty());
        assertEquals(ErrorCode.COURSE_NOT_FOUND,
                assertThrows(AppException.class,
                        () -> service.updateEnrollment(1L, request(4L, 5L))).getErrorCode());
        assertEquals(2L, enrollment.getStudent().getId());
        verify(enrollmentRepository, never()).save(any(Enrollment.class));
    }

    @Test
    void deletesExistingEnrollment() {
        Enrollment enrollment = enrollment(1L, 2L, 3L);
        when(enrollmentRepository.findById(1L)).thenReturn(Optional.of(enrollment));

        service.deleteEnrollment(1L);

        verify(enrollmentRepository).delete(enrollment);
    }

    @Test
    void exposesAllCrudEndpoints() throws Exception {
        EnrollmentService apiService = mock(EnrollmentService.class);
        MockMvc mvc = mvc(apiService);
        EnrollmentResponse response = new EnrollmentResponse(1L, 2L, 3L, "ACTIVE",
                LocalDateTime.of(2026, 9, 29, 10, 0));
        when(apiService.createEnrollment(any(EnrollmentRequest.class))).thenReturn(response);
        when(apiService.getAllEnrollments()).thenReturn(List.of(response));
        when(apiService.getEnrollmentById(1L)).thenReturn(response);
        when(apiService.updateEnrollment(any(Long.class), any(EnrollmentRequest.class)))
                .thenReturn(response);

        mvc.perform(post("/api/enrollments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":2,\"courseId\":3,\"status\":\"ACTIVE\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1));
        mvc.perform(get("/api/enrollments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].studentId").value(2));
        mvc.perform(get("/api/enrollments/{id}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courseId").value(3));
        mvc.perform(put("/api/enrollments/{id}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":2,\"courseId\":3,\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        mvc.perform(delete("/api/enrollments/{id}", 1L))
                .andExpect(status().isNoContent());
        verify(apiService).deleteEnrollment(1L);
    }

    @Test
    void duplicateCreateReturnsConflictResponse() throws Exception {
        EnrollmentService apiService = mock(EnrollmentService.class);
        MockMvc mvc = mvc(apiService);
        when(apiService.createEnrollment(any(EnrollmentRequest.class)))
                .thenThrow(new AppException(ErrorCode.ENROLLMENT_ALREADY_EXISTS));

        mvc.perform(post("/api/enrollments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":2,\"courseId\":3,\"status\":\"ACTIVE\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ENROLLMENT_ALREADY_EXISTS"));
    }

    @Test
    void invalidCreateAndUpdateDoNotReachService() throws Exception {
        EnrollmentService apiService = mock(EnrollmentService.class);
        MockMvc mvc = mvc(apiService);

        mvc.perform(post("/api/enrollments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":null,\"courseId\":3,\"status\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(put("/api/enrollments/{id}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":null,\"courseId\":3,\"status\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        verifyNoInteractions(apiService);
    }

    @Test
    void exposesNotFoundAndConflictResponses() throws Exception {
        EnrollmentService apiService = mock(EnrollmentService.class);
        MockMvc mvc = mvc(apiService);
        when(apiService.getEnrollmentById(99L))
                .thenThrow(new AppException(ErrorCode.ENROLLMENT_NOT_FOUND));
        when(apiService.updateEnrollment(any(Long.class), any(EnrollmentRequest.class)))
                .thenThrow(new AppException(ErrorCode.ENROLLMENT_ALREADY_EXISTS));
        doThrow(new AppException(ErrorCode.ENROLLMENT_NOT_FOUND))
                .when(apiService).deleteEnrollment(99L);

        mvc.perform(get("/api/enrollments/{id}", 99L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ENROLLMENT_NOT_FOUND"));
        mvc.perform(put("/api/enrollments/{id}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":2,\"courseId\":3,\"status\":\"ACTIVE\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ENROLLMENT_ALREADY_EXISTS"));
        mvc.perform(delete("/api/enrollments/{id}", 99L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ENROLLMENT_NOT_FOUND"));
    }

    private MockMvc mvc(EnrollmentService apiService) {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        return MockMvcBuilders.standaloneSetup(new EnrollmentController(apiService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    private EnrollmentRequest request(Long studentId, Long courseId) {
        EnrollmentRequest request = new EnrollmentRequest();
        request.setStudentId(studentId);
        request.setCourseId(courseId);
        request.setStatus("ACTIVE");
        return request;
    }

    private Enrollment enrollment(Long id, Long studentId, Long courseId) {
        Enrollment enrollment = new Enrollment();
        enrollment.setId(id);
        enrollment.setStudent(student(studentId));
        enrollment.setCourse(course(courseId));
        enrollment.setStatus("ACTIVE");
        enrollment.setEnrolledAt(LocalDateTime.of(2026, 9, 29, 10, 0));
        return enrollment;
    }

    private Student student(Long id) {
        Student student = new Student();
        student.setId(id);
        return student;
    }

    private Course course(Long id) {
        Course course = new Course();
        course.setId(id);
        return course;
    }
}
