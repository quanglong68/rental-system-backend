package fptu.semester8.mss301.propertiesservice.features.roomprice.api;

import fptu.exe202.signify.apiresponse.response.ApiResponse;
import fptu.semester8.mss301.propertiesservice.features.roomprice.api.dto.RoomPriceResponse;
import fptu.semester8.mss301.propertiesservice.features.roomprice.api.dto.SetRoomPriceRequest;
import fptu.semester8.mss301.propertiesservice.features.roomprice.application.RoomPriceService;
import fptu.semester8.mss301.propertiesservice.features.roomprice.mapper.RoomPriceMapper;
import fptu.semester8.mss301.propertiesservice.config.PropertyScope;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
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
    PropertyScope scope;

    @PostMapping
    @Operation(
            summary = "Đặt giá phòng mới",
            description = "Đặt giá niêm yết từ ngày hiệu lực. Ngày hiệu lực không được trong quá khứ; khoảng giá trước đó sẽ tự kết thúc vào ngày liền trước.")
    public ApiResponse<RoomPriceResponse> setPrice(
            @io.swagger.v3.oas.annotations.Parameter(schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string", example = "898785309339156480")) @PathVariable @Positive long roomId,
            @Valid @RequestBody SetRoomPriceRequest request
    ) {
        scope.requireRoom(roomId);
        return ApiResponse.success("Room price set", mapper.toResponse(service.setPrice(roomId, request)));
    }

    @GetMapping("/current")
    @Operation(
            summary = "Xem giá tại ngày",
            description = "Trả về giá niêm yết có hiệu lực tại ngày chỉ định; nếu không truyền date thì dùng ngày hiện tại."
    )
    public ApiResponse<RoomPriceResponse> getPrice(
            @io.swagger.v3.oas.annotations.Parameter(schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string", example = "898785309339156480")) @PathVariable @Positive long roomId,
            @RequestParam(required = false) LocalDate date
    ) {
        LocalDate targetDate = date == null ? LocalDate.now() : date;
        return ApiResponse.success(mapper.toResponse(service.getPriceForDate(roomId, targetDate)));
    }

    @GetMapping("/history")
    @Operation(
            summary = "Lịch sử giá phòng",
            description = "Liệt kê các khoảng giá niêm yết theo ngày hiệu lực giảm dần.")
    public ApiResponse<List<RoomPriceResponse>> history(
            @io.swagger.v3.oas.annotations.Parameter(schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string", example = "898785309339156480")) @PathVariable @Positive long roomId
    ) {
        scope.requireRoom(roomId);
        return ApiResponse.success(service.history(roomId).stream().map(mapper::toResponse).toList());
    }
}
