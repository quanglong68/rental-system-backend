package fptu.semester8.mss301.propertiesservice.features.room.api.dto;

import jakarta.validation.constraints.*;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Dữ liệu tạo hoặc cập nhật phòng")
public record RoomRequest(
        @Schema(type = "string", description = "ID tòa nhà dạng chuỗi số dương", example = "898785309339156480") @NotNull @Positive Long buildingId,
        @Schema(type = "string", description = "ID loại phòng dạng chuỗi số dương", example = "898785309339156481") @NotNull @Positive Long roomTypeId,
        @Schema(description = "Mã phòng, bắt buộc, tối đa 50 ký tự", example = "A-101") @NotBlank @Size(max = 50) String roomCode,
        @Schema(description = "Sức chứa tối đa, ít nhất 1 người", example = "4") @NotNull @Min(1) Integer maxCapacity,
        @Schema(description = "Diện tích, phải lớn hơn 0", example = "35.5") @NotNull @DecimalMin(value = "0.01") BigDecimal area,
        @Schema(description = "Số phòng ngủ, không âm", example = "1") @NotNull @Min(0) Integer bedroomCount,
        @Schema(description = "Số phòng tắm, không âm", example = "1") @NotNull @Min(0) Integer bathroomCount,
        @Schema(description = "Giá cơ bản, không âm", example = "5000000") @NotNull @DecimalMin(value = "0") BigDecimal basePrice,
        @Schema(description = "Trạng thái phòng", example = "AVAILABLE") @NotNull String status
) {
}
