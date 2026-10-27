package fptu.semester8.mss301.propertiesservice.features.roomtype.api;

import fptu.semester8.mss301.propertiesservice.features.roomtype.api.dto.*;
import fptu.semester8.mss301.propertiesservice.features.roomtype.application.RoomTypeService;
import fptu.semester8.mss301.propertiesservice.features.roomtype.mapper.RoomTypeMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/room-types")
@Tag(name = "Loại phòng", description = "Danh mục loại phòng cho thuê")
@RequiredArgsConstructor
@FieldDefaults(level = lombok.AccessLevel.PRIVATE, makeFinal = true)
public class RoomTypeController {

    RoomTypeService service;
    RoomTypeMapper mapper;

    @PostMapping
    @Operation(summary = "Tạo loại phòng", description = "Tạo loại phòng mới; typeCode bắt buộc, tối đa 50 ký tự; name bắt buộc, tối đa 100 ký tự; description không bắt buộc.")
    public ResponseEntity<RoomTypeResponse> create(@Valid @RequestBody RoomTypeRequest r) {
        var created = mapper.toResponse(service.create(r));
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Lấy loại phòng theo ID", description = "Trả về loại phòng theo ID.")
    public RoomTypeResponse get(
            @io.swagger.v3.oas.annotations.Parameter(schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string", example = "898785309339156480"))
            @PathVariable @Positive long id) {
        return mapper.toResponse(service.get(id));
    }

    @GetMapping
    @Operation(summary = "Liệt kê loại phòng", description = "Danh sách phân trang, sắp xếp createdAt giảm dần. page mặc định 0, size mặc định 20.")
    public Page<RoomTypeResponse> list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.list(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))).map(mapper::toResponse);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật loại phòng", description = "Cập nhật mã, tên và mô tả loại phòng theo ID.")
    public RoomTypeResponse update(
            @io.swagger.v3.oas.annotations.Parameter(schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string", example = "898785309339156480"))
            @PathVariable @Positive long id,
            @Valid @RequestBody RoomTypeRequest r) {
        return mapper.toResponse(service.update(id, r));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Xóa loại phòng", description = "Xóa loại phòng theo ID.")
    public void delete(
            @io.swagger.v3.oas.annotations.Parameter(schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string", example = "898785309339156480"))
            @PathVariable @Positive long id) {
        service.delete(id);
    }
}
