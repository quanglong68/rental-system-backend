package fptu.semester8.mss301.propertiesservice.features.roomprice.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "Giá niêm yết mới cho phòng")
public record SetRoomPriceRequest(
        @Schema(description = "Giá niêm yết, không âm", example = "5000000")
        @NotNull @DecimalMin("0") BigDecimal price,
        @Schema(description = "Ngày bắt đầu áp dụng, không được trong quá khứ", example = "2030-01-01")
        @NotNull LocalDate effectiveFrom
) {
}
