package com.ccnlthd.course_management.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class StudentRequest {

    @NotBlank(message = "Student full name is required")
    @Size(max = 150, message = "Student full name must not exceed 150 characters")
    private String fullName;

    @NotBlank(message = "Student email is required")
    @Email(message = "Student email must be valid")
    @Size(max = 150, message = "Student email must not exceed 150 characters")
    private String email;

    @Size(max = 20, message = "Student phone must not exceed 20 characters")
    private String phone;

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }
}
