package fptu.semester8.mss301.propertiesservice.features.province.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Dữ liệu tỉnh/thành")
public record ProvinceRequest(
        @Schema(description = "Mã tỉnh/thành, bắt buộc, tối đa 20 ký tự", example = "01") @NotBlank @Size(max = 20) String code,
        @Schema(description = "Tên tỉnh/thành, bắt buộc, tối đa 150 ký tự", example = "Hà Nội") @NotBlank @Size(max = 150) String name,
        @Schema(description = "Loại đơn vị hành chính, tối đa 100 ký tự", example = "Thành phố trực thuộc trung ương") @Size(max = 100) String divisionType
) {
}
