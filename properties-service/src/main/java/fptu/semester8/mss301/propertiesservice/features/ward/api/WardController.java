package fptu.semester8.mss301.propertiesservice.features.ward.api;

import fptu.semester8.mss301.propertiesservice.features.ward.api.dto.*;
import fptu.semester8.mss301.propertiesservice.features.ward.application.WardService;
import fptu.semester8.mss301.propertiesservice.features.ward.mapper.WardMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;

@RestController
@RequestMapping("/api/v1/wards")
@Tag(name = "Phường", description = "Quản lý và tra cứu phường/xã")

@RequiredArgsConstructor
@FieldDefaults(level = lombok.AccessLevel.PRIVATE, makeFinal = true)
public class WardController {

    WardService service;
    WardMapper mapper;

    @PostMapping
    @Operation(summary = "Tạo phường", description = "Tạo đơn vị hành chính trực thuộc tỉnh.")
    public ResponseEntity<WardResponse> create(@Valid @RequestBody WardRequest r) {
        var created = mapper.toResponse(service.create(r.provinceId(), r.code(), r.name(), r.divisionType()));
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.wardId()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping
    @Operation(summary = "Liệt kê phường", description = "Trả về danh sách tất cả phường.")
    public List<WardResponse> list() {
        return service.list().stream().map(mapper::toResponse).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Lấy phường theo ID", description = "Trả về thông tin phường theo wardId.")
    public WardResponse get(
            @io.swagger.v3.oas.annotations.Parameter(schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string", example = "898785309339156480"))
            @PathVariable @Positive long id) {
        return mapper.toResponse(service.get(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật phường", description = "Cập nhật tỉnh trực thuộc, mã, tên và loại đơn vị hành chính.")
    public WardResponse update(
            @io.swagger.v3.oas.annotations.Parameter(schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string", example = "898785309339156480"))
            @PathVariable @Positive long id,
            @Valid @RequestBody WardRequest r) {
        return mapper.toResponse(service.update(id, r.provinceId(), r.code(), r.name(), r.divisionType()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Xóa phường", description = "Xóa phường theo wardId.")
    public void delete(
            @io.swagger.v3.oas.annotations.Parameter(schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string", example = "898785309339156480"))
            @PathVariable @Positive long id) {
        service.delete(id);
    }

    @GetMapping("/province/{provinceId}")
    @Operation(summary = "Liệt kê phường theo tỉnh", description = "Trả về danh sách phường thuộc provinceId chỉ định.")
    public List<WardResponse> byProvince(
            @io.swagger.v3.oas.annotations.Parameter(schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string", example = "898785309339156480"))
            @PathVariable @Positive long provinceId) {
        return service.getWardsByProvinceId(provinceId).stream().map(mapper::toResponse).toList();
    }
}
