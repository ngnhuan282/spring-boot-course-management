package com.ccnlthd.course_management;

import com.ccnlthd.course_management.dto.request.CategoryRequest;
import com.ccnlthd.course_management.dto.request.EnrollmentRequest;
import com.ccnlthd.course_management.dto.request.StudentRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestDtoValidationTests {

    private Validator validator;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validatorFactory = new LocalValidatorFactoryBean();
        validatorFactory.afterPropertiesSet();
        validator = validatorFactory;
    }

    @Test
    void shouldValidateCategoryRequest() {
        CategoryRequest request = new CategoryRequest();
        request.setName("");

        Set<ConstraintViolation<CategoryRequest>> violations = validator.validate(request);

        assertFalse(violations.isEmpty());
        assertTrue(hasViolationOn(violations, "name"));
    }

    @Test
    void shouldValidateStudentEmail() {
        StudentRequest request = new StudentRequest();
        request.setFullName("Nguyen Van An");
        request.setEmail("invalid-email");

        Set<ConstraintViolation<StudentRequest>> violations = validator.validate(request);

        assertFalse(violations.isEmpty());
        assertTrue(hasViolationOn(violations, "email"));
    }

    @Test
    void shouldValidateEnrollmentRequiredIds() {
        EnrollmentRequest request = new EnrollmentRequest();
        request.setStatus("ACTIVE");

        Set<ConstraintViolation<EnrollmentRequest>> violations = validator.validate(request);

        assertFalse(violations.isEmpty());
        assertTrue(hasViolationOn(violations, "studentId"));
        assertTrue(hasViolationOn(violations, "courseId"));
    }

    private boolean hasViolationOn(
            Set<? extends ConstraintViolation<?>> violations,
            String propertyName
    ) {
        return violations.stream()
                .anyMatch(violation -> propertyName.equals(
                        violation.getPropertyPath().toString()
                ));
    }
}
