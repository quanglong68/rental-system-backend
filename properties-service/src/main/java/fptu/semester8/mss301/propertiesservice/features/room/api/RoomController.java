package fptu.semester8.mss301.propertiesservice.features.room.api;

import fptu.semester8.mss301.propertiesservice.features.room.api.dto.*;
import fptu.semester8.mss301.propertiesservice.features.room.application.RoomService;
import fptu.semester8.mss301.propertiesservice.features.room.mapper.RoomMapper;
import fptu.semester8.mss301.propertiesservice.config.PropertyScope;
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
@RequestMapping("/api/v1/rooms")
@Tag(name = "Phòng", description = "Quản lý phòng cho thuê trong các tòa nhà")
@RequiredArgsConstructor
@FieldDefaults(level = lombok.AccessLevel.PRIVATE, makeFinal = true)
public class RoomController {

    RoomService service;
    RoomMapper mapper;
    PropertyScope scope;

    @PostMapping
    @Operation(summary = "Tạo phòng", description = "Tạo phòng với tòa nhà, loại phòng, mã, sức chứa, diện tích, số phòng ngủ/tắm, giá cơ bản và trạng thái.")
    public ResponseEntity<RoomResponse> create(@Valid @RequestBody RoomRequest r) {
        scope.requireBuilding(r.buildingId());
        var created = mapper.toResponse(service.create(r));
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Lấy phòng theo ID", description = "Trả về chi tiết phòng; ID không tồn tại sẽ tạo lỗi không tìm thấy.")
    public RoomResponse get(
            @io.swagger.v3.oas.annotations.Parameter(schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string", example = "898785309339156480"))
            @PathVariable @Positive long id) {
        return mapper.toResponse(service.get(id));
    }

    @GetMapping
    @Operation(summary = "Liệt kê phòng", description = "Danh sách phân trang, có thể lọc theo buildingId. page mặc định 0, size mặc định 20 (tối đa 100).")
    public Page<RoomResponse> list(
            @io.swagger.v3.oas.annotations.Parameter(schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string", example = "898785309339156480"))
            @RequestParam(required = false) @Positive Long buildingId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        if (page < 0 || size < 1 || size > 100) throw new IllegalArgumentException("invalid pagination");
        String[] s = sort.split(",", 2);
        if (!s[0].matches("createdAt|updatedAt|roomCode|status|basePrice"))
            throw new IllegalArgumentException("invalid sort field");
        var pageable = PageRequest.of(page, size, Sort.by(
                s.length > 1 && s[1].equalsIgnoreCase("asc") ? Sort.Direction.ASC : Sort.Direction.DESC, s[0]));
        var results = scope.isAdmin() ? service.list(buildingId, pageable)
                : service.listScoped(buildingId, scope.assignedBuildings(), pageable);
        return results.map(mapper::toResponse);
    }

    @GetMapping("/building/{buildingId}")
    @Operation(summary = "Liệt kê phòng theo tòa nhà", description = "Trả về phòng thuộc tòa nhà chỉ định, phân trang với page mặc định 0 và size mặc định 20.")
    public Page<RoomResponse> byBuilding(
            @io.swagger.v3.oas.annotations.Parameter(schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string", example = "898785309339156480"))
            @PathVariable @Positive long buildingId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        scope.requireBuilding(buildingId);
        return service.list(buildingId, PageRequest.of(page, size)).map(mapper::toResponse);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật phòng", description = "Cập nhật thông tin phòng theo ID.")
    public RoomResponse update(
            @io.swagger.v3.oas.annotations.Parameter(schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string", example = "898785309339156480"))
            @PathVariable @Positive long id,
            @Valid @RequestBody RoomRequest r) {
        scope.requireRoom(id);
        scope.requireBuilding(r.buildingId());
        return mapper.toResponse(service.update(id, r));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Xóa phòng", description = "Xóa phòng theo ID.")
    public void delete(
            @io.swagger.v3.oas.annotations.Parameter(schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string", example = "898785309339156480"))
            @PathVariable @Positive long id) {
        service.delete(id);
    }
}
