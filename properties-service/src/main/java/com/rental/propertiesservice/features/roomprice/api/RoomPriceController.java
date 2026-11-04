package com.rental.propertiesservice.features.roomprice.api;

import com.rental.propertiesservice.features.roomprice.api.dto.RoomPriceResponse;
import com.rental.propertiesservice.features.roomprice.api.dto.SetRoomPriceRequest;
import com.rental.propertiesservice.features.roomprice.application.RoomPriceService;
import com.rental.propertiesservice.features.roomprice.mapper.RoomPriceMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/rooms/{roomId}/prices")
@Tag(name = "Giá phòng", description = "Quản lý giá niêm yết và lịch sử giá phòng")
@RequiredArgsConstructor
@FieldDefaults(level = lombok.AccessLevel.PRIVATE, makeFinal = true)
public class RoomPriceController {

    RoomPriceService service;

    RoomPriceMapper mapper;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','QUAN_LY')")
    @Operation(
            summary = "Đặt giá phòng mới",
            description = "Đặt giá niêm yết từ ngày hiệu lực. Ngày hiệu lực không được trong quá khứ; khoảng giá trước đó sẽ tự kết thúc vào ngày liền trước.")
    public RoomPriceResponse setPrice(
            @io.swagger.v3.oas.annotations.Parameter(schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string", example = "898785309339156480")) @PathVariable @Positive long roomId,
            @Valid @RequestBody SetRoomPriceRequest request
    ) {
        return mapper.toResponse(service.setPrice(roomId, request));
    }

    @GetMapping("/current")
    @Operation(
            summary = "Xem giá tại ngày",
            description = "Trả về giá niêm yết có hiệu lực tại ngày chỉ định; nếu không truyền date thì dùng ngày hiện tại."
    )
    public RoomPriceResponse getPrice(
            @io.swagger.v3.oas.annotations.Parameter(schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string", example = "898785309339156480")) @PathVariable @Positive long roomId,
            @RequestParam(required = false) LocalDate date
    ) {
        LocalDate targetDate = date == null ? LocalDate.now() : date;
        return mapper.toResponse(service.getPriceForDate(roomId, targetDate));
    }

    @GetMapping("/history")
    @PreAuthorize("hasAnyRole('ADMIN','QUAN_LY','SALE','KY_THUAT')")
    @Operation(
            summary = "Lịch sử giá phòng",
            description = "Liệt kê các khoảng giá niêm yết theo ngày hiệu lực giảm dần.")
    public List<RoomPriceResponse> history(
            @io.swagger.v3.oas.annotations.Parameter(schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string", example = "898785309339156480")) @PathVariable @Positive long roomId
    ) {
        return service.history(roomId).stream().map(mapper::toResponse).toList();
    }
}
