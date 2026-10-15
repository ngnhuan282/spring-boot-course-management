package com.ccnlthd.course_management.controller;

import com.ccnlthd.course_management.dto.request.CategoryRequest;
import com.ccnlthd.course_management.dto.response.CategoryResponse;
import com.ccnlthd.course_management.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
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
@RequestMapping("/api/categories")
@RequiredArgsConstructor
@Tag(name = "Category", description = "Quản lý danh mục khóa học")
public class CategoryController {

    private final CategoryService categoryService;

    @PostMapping
    @Operation(summary = "Tạo danh mục", description = "Tạo danh mục mới; tên được bỏ khoảng trắng đầu/cuối và kiểm tra trùng không phân biệt hoa/thường.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Tạo thành công",
                    content = @Content(schema = @Schema(implementation = CategoryResponse.class))),
            @ApiResponse(responseCode = "400", description = "Dữ liệu danh mục không hợp lệ",
                    content = @Content(schema = @Schema(implementation = com.ccnlthd.course_management.dto.response.ApiResponse.class))),
            @ApiResponse(responseCode = "409", description = "Tên danh mục đã tồn tại: CATEGORY_ALREADY_EXISTS; giữ nguyên dữ liệu danh mục. "
                    + "Vi phạm ràng buộc CSDL, kể cả khi ghi đồng thời: DATA_CONFLICT.",
                    content = @Content(schema = @Schema(implementation = com.ccnlthd.course_management.dto.response.ApiResponse.class),
                            examples = {
                                    @ExampleObject(name = "CATEGORY_ALREADY_EXISTS", value = "{\"code\":\"CATEGORY_ALREADY_EXISTS\",\"message\":\"Category name already exists\",\"result\":null,\"timestamp\":\"2026-10-15T10:00:00\"}"),
                                    @ExampleObject(name = "DATA_CONFLICT", value = "{\"code\":\"DATA_CONFLICT\",\"message\":\"Data conflicts with an existing record\",\"result\":null,\"timestamp\":\"2026-10-15T10:00:00\"}")
                            })),
            @ApiResponse(responseCode = "500", description = "Lỗi không mong đợi",
                    content = @Content(schema = @Schema(implementation = com.ccnlthd.course_management.dto.response.ApiResponse.class)))
    })
    public ResponseEntity<CategoryResponse> createCategory(
            @Valid @RequestBody CategoryRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(categoryService.createCategory(request));
    }

    @GetMapping
    @Operation(summary = "Xem danh sách danh mục", description = "Trả về tất cả danh mục.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lấy danh sách thành công",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = CategoryResponse.class)))),
            @ApiResponse(responseCode = "500", description = "Lỗi không mong đợi",
                    content = @Content(schema = @Schema(implementation = com.ccnlthd.course_management.dto.response.ApiResponse.class)))
    })
    public ResponseEntity<List<CategoryResponse>> getAllCategories() {
        return ResponseEntity.ok(categoryService.getAllCategories());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Xem chi tiết danh mục", description = "Tìm danh mục theo ID.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tìm thấy danh mục",
                    content = @Content(schema = @Schema(implementation = CategoryResponse.class))),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy danh mục (CATEGORY_NOT_FOUND)",
                    content = @Content(schema = @Schema(implementation = com.ccnlthd.course_management.dto.response.ApiResponse.class))),
            @ApiResponse(responseCode = "500", description = "Lỗi không mong đợi",
                    content = @Content(schema = @Schema(implementation = com.ccnlthd.course_management.dto.response.ApiResponse.class)))
    })
    public ResponseEntity<CategoryResponse> getCategoryById(@PathVariable Long id) {
        return ResponseEntity.ok(categoryService.getCategoryById(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật danh mục", description = "Sửa danh mục; tên được bỏ khoảng trắng đầu/cuối và kiểm tra trùng với danh mục khác không phân biệt hoa/thường. Được giữ tên hiện tại.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cập nhật thành công",
                    content = @Content(schema = @Schema(implementation = CategoryResponse.class))),
            @ApiResponse(responseCode = "400", description = "Dữ liệu danh mục không hợp lệ",
                    content = @Content(schema = @Schema(implementation = com.ccnlthd.course_management.dto.response.ApiResponse.class))),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy danh mục (CATEGORY_NOT_FOUND)",
                    content = @Content(schema = @Schema(implementation = com.ccnlthd.course_management.dto.response.ApiResponse.class))),
            @ApiResponse(responseCode = "409", description = "Tên đã thuộc danh mục khác: CATEGORY_ALREADY_EXISTS; giữ nguyên dữ liệu danh mục. "
                    + "Vi phạm ràng buộc CSDL, kể cả khi ghi đồng thời: DATA_CONFLICT.",
                    content = @Content(schema = @Schema(implementation = com.ccnlthd.course_management.dto.response.ApiResponse.class),
                            examples = {
                                    @ExampleObject(name = "CATEGORY_ALREADY_EXISTS", value = "{\"code\":\"CATEGORY_ALREADY_EXISTS\",\"message\":\"Category name already exists\",\"result\":null,\"timestamp\":\"2026-10-15T10:00:00\"}"),
                                    @ExampleObject(name = "DATA_CONFLICT", value = "{\"code\":\"DATA_CONFLICT\",\"message\":\"Data conflicts with an existing record\",\"result\":null,\"timestamp\":\"2026-10-15T10:00:00\"}")
                            })),
            @ApiResponse(responseCode = "500", description = "Lỗi không mong đợi",
                    content = @Content(schema = @Schema(implementation = com.ccnlthd.course_management.dto.response.ApiResponse.class)))
    })
    public ResponseEntity<CategoryResponse> updateCategory(
            @PathVariable Long id,
            @Valid @RequestBody CategoryRequest request
    ) {
        return ResponseEntity.ok(categoryService.updateCategory(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Xóa danh mục", description = "Chỉ xóa danh mục chưa có khóa học tham chiếu.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Xóa thành công, không có nội dung trả về"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy danh mục (CATEGORY_NOT_FOUND)",
                    content = @Content(schema = @Schema(implementation = com.ccnlthd.course_management.dto.response.ApiResponse.class))),
            @ApiResponse(responseCode = "409", description = "Danh mục có ít nhất một khóa học: CATEGORY_HAS_COURSES; giữ nguyên danh mục và các khóa học. "
                    + "Vi phạm ràng buộc CSDL, kể cả khi ghi đồng thời: DATA_CONFLICT.",
                    content = @Content(schema = @Schema(implementation = com.ccnlthd.course_management.dto.response.ApiResponse.class),
                            examples = {
                                    @ExampleObject(name = "CATEGORY_HAS_COURSES", value = "{\"code\":\"CATEGORY_HAS_COURSES\",\"message\":\"Category has courses\",\"result\":null,\"timestamp\":\"2026-10-15T10:00:00\"}"),
                                    @ExampleObject(name = "DATA_CONFLICT", value = "{\"code\":\"DATA_CONFLICT\",\"message\":\"Data conflicts with an existing record\",\"result\":null,\"timestamp\":\"2026-10-15T10:00:00\"}")
                            })),
            @ApiResponse(responseCode = "500", description = "Lỗi không mong đợi",
                    content = @Content(schema = @Schema(implementation = com.ccnlthd.course_management.dto.response.ApiResponse.class)))
    })
    public ResponseEntity<Void> deleteCategory(@PathVariable Long id) {
        categoryService.deleteCategory(id);
        return ResponseEntity.noContent().build();
    }
}
