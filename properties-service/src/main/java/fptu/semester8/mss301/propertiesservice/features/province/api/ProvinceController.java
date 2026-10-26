package fptu.semester8.mss301.propertiesservice.features.province.api;

import fptu.exe202.signify.apiresponse.response.ApiResponse;
import fptu.semester8.mss301.propertiesservice.features.province.api.dto.*;
import fptu.semester8.mss301.propertiesservice.features.province.application.ProvinceService;
import fptu.semester8.mss301.propertiesservice.features.province.mapper.ProvinceMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/provinces")
@Tag(name = "Tỉnh thành", description = "Quản lý và tra cứu tỉnh/thành phố")
@RequiredArgsConstructor
public class ProvinceController {
    private final ProvinceService service;
    private final ProvinceMapper mapper;

    @PostMapping
    @Operation(summary = "Tạo tỉnh", description = "Tạo tỉnh/thành mới. code và name bắt buộc; divisionType không bắt buộc.")
    public ApiResponse<ProvinceResponse> create(@Valid @RequestBody ProvinceRequest r) {
        return ApiResponse.success("Province created", mapper.toResponse(service.create(r.code(), r.name(), r.divisionType())));
    }

    @GetMapping
    @Operation(summary = "Liệt kê tỉnh", description = "Trả về danh sách tất cả tỉnh/thành.")
    public ApiResponse<List<ProvinceResponse>> list() {
        return ApiResponse.success(service.list().stream().map(mapper::toResponse).toList());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Lấy tỉnh theo ID", description = "Trả về thông tin tỉnh theo provinceId.")
    public ApiResponse<ProvinceResponse> get(@io.swagger.v3.oas.annotations.Parameter(schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string", example = "898785309339156480")) @PathVariable @Positive long id) {
        return ApiResponse.success(mapper.toResponse(service.get(id)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật tỉnh", description = "Cập nhật mã, tên và loại đơn vị hành chính của tỉnh.")
    public ApiResponse<ProvinceResponse> update(@io.swagger.v3.oas.annotations.Parameter(schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string", example = "898785309339156480")) @PathVariable @Positive long id, @Valid @RequestBody ProvinceRequest r) {
        return ApiResponse.success("Province updated", mapper.toResponse(service.update(id, r.code(), r.name(), r.divisionType())));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Xóa tỉnh", description = "Xóa tỉnh theo provinceId.")
    public ApiResponse<Void> delete(@io.swagger.v3.oas.annotations.Parameter(schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string", example = "898785309339156480")) @PathVariable @Positive long id) {
        service.delete(id);
        return ApiResponse.success("Province deleted", null);
    }
}
