package com.rental.propertiesservice.features.room.api;

import com.rental.common.dto.PageResponse;
import com.rental.common.error.BusinessException;
import com.rental.propertiesservice.features.room.api.dto.*;
import com.rental.propertiesservice.features.room.application.RoomService;
import com.rental.propertiesservice.features.room.mapper.RoomMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/rooms")
@Tag(name = "Phòng", description = "Quản lý phòng cho thuê trong các tòa nhà")
@RequiredArgsConstructor
@FieldDefaults(level = lombok.AccessLevel.PRIVATE, makeFinal = true)
public class RoomController {

    RoomService service;
    RoomMapper mapper;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','QUAN_LY')")
    @Operation(summary = "Tạo phòng", description = "Tạo phòng với tòa nhà, loại phòng, mã, sức chứa, diện tích, số phòng ngủ/tắm, giá cơ bản và trạng thái. Trường bắt buộc được kiểm tra theo RoomRequest.")
    public RoomResponse create(@Valid @RequestBody RoomRequest r) {
        return mapper.toResponse(service.create(r));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Lấy phòng theo ID", description = "Trả về chi tiết phòng; ID không tồn tại sẽ tạo lỗi không tìm thấy.")
    public RoomResponse get(@io.swagger.v3.oas.annotations.Parameter(schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string", example = "898785309339156480")) @PathVariable @Positive long id) {
        return mapper.toResponse(service.get(id));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','QUAN_LY','SALE','KY_THUAT')")
    @Operation(summary = "Liệt kê phòng", description = "Danh sách phân trang, có thể lọc theo buildingId. page mặc định 0, size mặc định 20 (tối đa 100), sort mặc định createdAt,desc; trường sort: createdAt, updatedAt, roomCode, status, basePrice.")
    public PageResponse<RoomResponse> list(@io.swagger.v3.oas.annotations.Parameter(schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string", example = "898785309339156480")) @RequestParam(required = false) @Positive Long buildingId, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size, @RequestParam(defaultValue = "createdAt,desc") String sort) {
        if (page < 0 || size < 1 || size > 100) {
            throw invalidPage();
        }
        String[] s = sort.split(",", 2);
        if (!s[0].matches("createdAt|updatedAt|roomCode|status|basePrice")) {
            throw new BusinessException("INVALID_SORT", "sort không hợp lệ", HttpStatus.BAD_REQUEST);
        }
        return PageResponse.of(service.list(buildingId, PageRequest.of(page, size,
                Sort.by(s.length > 1 && s[1].equalsIgnoreCase("asc") ? Sort.Direction.ASC : Sort.Direction.DESC, s[0])))
                .map(mapper::toResponse));
    }

    @GetMapping("/building/{buildingId}")
    @PreAuthorize("hasAnyRole('ADMIN','QUAN_LY','SALE','KY_THUAT')")
    @Operation(summary = "Liệt kê phòng theo tòa nhà", description = "Trả về phòng thuộc tòa nhà chỉ định, phân trang với page mặc định 0 và size mặc định 20.")
    public PageResponse<RoomResponse> byBuilding(@io.swagger.v3.oas.annotations.Parameter(schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string", example = "898785309339156480")) @PathVariable @Positive long buildingId, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw invalidPage();
        }
        return PageResponse.of(service.list(buildingId, PageRequest.of(page, size)).map(mapper::toResponse));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','QUAN_LY')")
    @Operation(summary = "Cập nhật phòng", description = "Cập nhật thông tin phòng theo ID bằng RoomRequest.")
    public RoomResponse update(@io.swagger.v3.oas.annotations.Parameter(schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string", example = "898785309339156480")) @PathVariable @Positive long id, @Valid @RequestBody RoomRequest r) {
        return mapper.toResponse(service.update(id, r));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','QUAN_LY')")
    @Operation(summary = "Đổi trạng thái phòng", description = "Chỉ cho phép chuyển tay giữa VACANT và MAINTENANCE; OCCUPIED/UPCOMING_VACANT do sự kiện hợp đồng quyết định.")
    public RoomResponse changeStatus(
            @io.swagger.v3.oas.annotations.Parameter(schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string", example = "898785309339156480")) @PathVariable @Positive long id,
            @Valid @RequestBody ChangeRoomStatusRequest request) {
        return mapper.toResponse(service.changeStatus(id, request.status()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN','QUAN_LY')")
    @Operation(summary = "Xóa phòng", description = "Xóa phòng theo ID.")
    public void delete(@io.swagger.v3.oas.annotations.Parameter(schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string", example = "898785309339156480")) @PathVariable @Positive long id) {
        service.delete(id);
    }

    private static BusinessException invalidPage() {
        return new BusinessException("INVALID_PAGE", "page phải >= 0 và size trong khoảng 1..100",
                HttpStatus.BAD_REQUEST);
    }
}
