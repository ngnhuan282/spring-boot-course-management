package com.ccnlthd.course_management.controller;

import com.ccnlthd.course_management.dto.request.StudentRequest;
import com.ccnlthd.course_management.dto.response.StudentResponse;
import com.ccnlthd.course_management.service.StudentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/students")
@RequiredArgsConstructor
@Tag(name = "Student", description = "Quản lý học viên")
public class StudentController {

    private final StudentService studentService;

    @PostMapping
    @Operation(summary = "Tạo học viên", description = "Tạo học viên mới sau khi kiểm tra email trùng.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Tạo thành công",
                    content = @Content(schema = @Schema(implementation = StudentResponse.class))),
            @ApiResponse(responseCode = "400", description = "Dữ liệu học viên không hợp lệ",
                    content = @Content(schema = @Schema(implementation = com.ccnlthd.course_management.dto.response.ApiResponse.class))),
            @ApiResponse(responseCode = "409", description = "Email đã tồn tại (STUDENT_EMAIL_ALREADY_EXISTS)",
                    content = @Content(schema = @Schema(implementation = com.ccnlthd.course_management.dto.response.ApiResponse.class))),
            @ApiResponse(responseCode = "500", description = "Lỗi không mong đợi",
                    content = @Content(schema = @Schema(implementation = com.ccnlthd.course_management.dto.response.ApiResponse.class)))
    })
    public ResponseEntity<StudentResponse> createStudent(@Valid @RequestBody StudentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(studentService.createStudent(request));
    }

    @GetMapping
    @Operation(summary = "Xem danh sách học viên", description = "Trả về tất cả học viên.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lấy danh sách thành công",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = StudentResponse.class)))),
            @ApiResponse(responseCode = "500", description = "Lỗi không mong đợi",
                    content = @Content(schema = @Schema(implementation = com.ccnlthd.course_management.dto.response.ApiResponse.class)))
    })
    public ResponseEntity<List<StudentResponse>> getAllStudents() {
        return ResponseEntity.ok(studentService.getAllStudents());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Xem chi tiết học viên", description = "Tìm học viên theo ID.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tìm thấy học viên",
                    content = @Content(schema = @Schema(implementation = StudentResponse.class))),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy học viên (STUDENT_NOT_FOUND)",
                    content = @Content(schema = @Schema(implementation = com.ccnlthd.course_management.dto.response.ApiResponse.class))),
            @ApiResponse(responseCode = "500", description = "Lỗi không mong đợi",
                    content = @Content(schema = @Schema(implementation = com.ccnlthd.course_management.dto.response.ApiResponse.class)))
    })
    public ResponseEntity<StudentResponse> getStudentById(@PathVariable Long id) {
        return ResponseEntity.ok(studentService.getStudentById(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật học viên", description = "Sửa thông tin học viên và kiểm tra email trùng với học viên khác.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cập nhật thành công",
                    content = @Content(schema = @Schema(implementation = StudentResponse.class))),
            @ApiResponse(responseCode = "400", description = "Dữ liệu học viên không hợp lệ",
                    content = @Content(schema = @Schema(implementation = com.ccnlthd.course_management.dto.response.ApiResponse.class))),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy học viên (STUDENT_NOT_FOUND)",
                    content = @Content(schema = @Schema(implementation = com.ccnlthd.course_management.dto.response.ApiResponse.class))),
            @ApiResponse(responseCode = "409", description = "Email đã thuộc học viên khác (STUDENT_EMAIL_ALREADY_EXISTS)",
                    content = @Content(schema = @Schema(implementation = com.ccnlthd.course_management.dto.response.ApiResponse.class))),
            @ApiResponse(responseCode = "500", description = "Lỗi không mong đợi",
                    content = @Content(schema = @Schema(implementation = com.ccnlthd.course_management.dto.response.ApiResponse.class)))
    })
    public ResponseEntity<StudentResponse> updateStudent(
            @PathVariable Long id,
            @Valid @RequestBody StudentRequest request
    ) {
        return ResponseEntity.ok(studentService.updateStudent(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Xóa học viên", description = "Chỉ xóa học viên chưa có lượt ghi danh.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Xóa thành công, không có nội dung trả về"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy học viên (STUDENT_NOT_FOUND)",
                    content = @Content(schema = @Schema(implementation = com.ccnlthd.course_management.dto.response.ApiResponse.class))),
            @ApiResponse(responseCode = "409", description = "Học viên đã có lượt ghi danh (STUDENT_HAS_ENROLLMENTS)",
                    content = @Content(schema = @Schema(implementation = com.ccnlthd.course_management.dto.response.ApiResponse.class))),
            @ApiResponse(responseCode = "500", description = "Lỗi không mong đợi",
                    content = @Content(schema = @Schema(implementation = com.ccnlthd.course_management.dto.response.ApiResponse.class)))
    })
    public ResponseEntity<Void> deleteStudent(@PathVariable Long id) {
        studentService.deleteStudent(id);
        return ResponseEntity.noContent().build();
    }
}
