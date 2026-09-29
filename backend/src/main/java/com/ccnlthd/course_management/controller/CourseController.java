package com.ccnlthd.course_management.controller;

import com.ccnlthd.course_management.dto.request.CourseRequest;
import com.ccnlthd.course_management.dto.response.CourseResponse;
import com.ccnlthd.course_management.service.CourseService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/courses")
@RequiredArgsConstructor
@Tag(name = "Course", description = "Quản lý khóa học")
public class CourseController {

    private final CourseService courseService;

    @PostMapping
    @Operation(summary = "Tạo khóa học", description = "Tạo khóa học trong danh mục đã tồn tại.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Tạo thành công",
                    content = @Content(schema = @Schema(implementation = CourseResponse.class))),
            @ApiResponse(responseCode = "400", description = "Dữ liệu khóa học không hợp lệ",
                    content = @Content(schema = @Schema(implementation = com.ccnlthd.course_management.dto.response.ApiResponse.class))),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy danh mục (CATEGORY_NOT_FOUND)",
                    content = @Content(schema = @Schema(implementation = com.ccnlthd.course_management.dto.response.ApiResponse.class))),
            @ApiResponse(responseCode = "500", description = "Lỗi không mong đợi",
                    content = @Content(schema = @Schema(implementation = com.ccnlthd.course_management.dto.response.ApiResponse.class)))
    })
    public ResponseEntity<CourseResponse> createCourse(
            @Valid @RequestBody CourseRequest request
    ) {
        CourseResponse response = courseService.createCourse(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Xem chi tiết khóa học", description = "Tìm khóa học theo ID.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tìm thấy khóa học",
                    content = @Content(schema = @Schema(implementation = CourseResponse.class))),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy khóa học (COURSE_NOT_FOUND)",
                    content = @Content(schema = @Schema(implementation = com.ccnlthd.course_management.dto.response.ApiResponse.class))),
            @ApiResponse(responseCode = "500", description = "Lỗi không mong đợi",
                    content = @Content(schema = @Schema(implementation = com.ccnlthd.course_management.dto.response.ApiResponse.class)))
    })
    public ResponseEntity<CourseResponse> getCourseById(@PathVariable Long id) {
        return ResponseEntity.ok(courseService.getCourseById(id));
    }

    @GetMapping
    @Operation(summary = "Xem danh sách khóa học", description = "Trả về tất cả khóa học.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lấy danh sách thành công",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = CourseResponse.class)))),
            @ApiResponse(responseCode = "500", description = "Lỗi không mong đợi",
                    content = @Content(schema = @Schema(implementation = com.ccnlthd.course_management.dto.response.ApiResponse.class)))
    })
    public ResponseEntity<List<CourseResponse>> getAllCourses() {
        return ResponseEntity.ok(courseService.getAllCourses());
    }

    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật khóa học", description = "Sửa thông tin khóa học và danh mục của khóa học.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cập nhật thành công",
                    content = @Content(schema = @Schema(implementation = CourseResponse.class))),
            @ApiResponse(responseCode = "400", description = "Dữ liệu khóa học không hợp lệ",
                    content = @Content(schema = @Schema(implementation = com.ccnlthd.course_management.dto.response.ApiResponse.class))),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy khóa học (COURSE_NOT_FOUND) hoặc danh mục (CATEGORY_NOT_FOUND)",
                    content = @Content(schema = @Schema(implementation = com.ccnlthd.course_management.dto.response.ApiResponse.class))),
            @ApiResponse(responseCode = "500", description = "Lỗi không mong đợi",
                    content = @Content(schema = @Schema(implementation = com.ccnlthd.course_management.dto.response.ApiResponse.class)))
    })
    public ResponseEntity<CourseResponse> updateCourse(
            @PathVariable Long id,
            @Valid @RequestBody CourseRequest request
    ) {
        return ResponseEntity.ok(courseService.updateCourse(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Xóa khóa học", description = "Chỉ xóa khóa học chưa có lượt ghi danh.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Xóa thành công, không có nội dung trả về"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy khóa học (COURSE_NOT_FOUND)",
                    content = @Content(schema = @Schema(implementation = com.ccnlthd.course_management.dto.response.ApiResponse.class))),
            @ApiResponse(responseCode = "409", description = "Khóa học đã có lượt ghi danh (COURSE_HAS_ENROLLMENTS)",
                    content = @Content(schema = @Schema(implementation = com.ccnlthd.course_management.dto.response.ApiResponse.class))),
            @ApiResponse(responseCode = "500", description = "Lỗi không mong đợi",
                    content = @Content(schema = @Schema(implementation = com.ccnlthd.course_management.dto.response.ApiResponse.class)))
    })
    public ResponseEntity<Void> deleteCourse(@PathVariable Long id) {
        courseService.deleteCourse(id);
        return ResponseEntity.noContent().build();
    }
}
