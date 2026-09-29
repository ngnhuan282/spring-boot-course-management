package com.ccnlthd.course_management.controller;

import com.ccnlthd.course_management.dto.request.EnrollmentRequest;
import com.ccnlthd.course_management.dto.response.EnrollmentResponse;
import com.ccnlthd.course_management.service.EnrollmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/enrollments")
@RequiredArgsConstructor
@Tag(name = "Enrollment", description = "Quản lý ghi danh khóa học")
public class EnrollmentController {

    private final EnrollmentService enrollmentService;

    @PostMapping
    @Operation(summary = "Tạo lượt ghi danh", description = "Ghi danh học viên vào khóa học nếu chưa ghi danh trước đó.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Ghi danh thành công",
                    content = @Content(schema = @Schema(implementation = EnrollmentResponse.class))),
            @ApiResponse(responseCode = "400", description = "Dữ liệu ghi danh không hợp lệ",
                    content = @Content(schema = @Schema(implementation = com.ccnlthd.course_management.dto.response.ApiResponse.class))),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy học viên (STUDENT_NOT_FOUND) hoặc khóa học (COURSE_NOT_FOUND)",
                    content = @Content(schema = @Schema(implementation = com.ccnlthd.course_management.dto.response.ApiResponse.class))),
            @ApiResponse(responseCode = "409", description = "Học viên đã ghi danh khóa học (ENROLLMENT_ALREADY_EXISTS)",
                    content = @Content(schema = @Schema(implementation = com.ccnlthd.course_management.dto.response.ApiResponse.class))),
            @ApiResponse(responseCode = "500", description = "Lỗi không mong đợi",
                    content = @Content(schema = @Schema(implementation = com.ccnlthd.course_management.dto.response.ApiResponse.class)))
    })
    public ResponseEntity<EnrollmentResponse> createEnrollment(
            @Valid @RequestBody EnrollmentRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(enrollmentService.createEnrollment(request));
    }
}
