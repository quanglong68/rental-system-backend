package com.rental.propertiesservice.features.building.api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "Dữ liệu tạo hoặc cập nhật tòa nhà")
public record BuildingRequest(
        @Schema(description = "Tên tòa nhà, bắt buộc, tối đa 200 ký tự", example = "Green Apartment") @NotBlank @Size(max = 200) String name,
        @Schema(description = "Địa chỉ, bắt buộc, tối đa 500 ký tự", example = "123 Nguyễn Huệ") @NotBlank @Size(max = 500) String address,
        @Schema(type = "string", description = "ID phường/xã dạng chuỗi số dương", example = "898785309339156480") @NotNull @Positive Long wardId,
        @Schema(type = "string", description = "ID tỉnh/thành phố dạng chuỗi số dương", example = "898785309339156481") @NotNull @Positive Long provinceId,
        @Schema(description = "Vĩ độ từ -90 đến 90", example = "10.7769") @NotNull @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
        @Schema(description = "Kinh độ từ -180 đến 180", example = "106.7009") @NotNull @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude,
        @Schema(description = "Mô tả tòa nhà") String description,
        @Schema(description = "Optional on update; omitted keeps current status. Creation always uses ACTIVE.",
                allowableValues = {"ACTIVE", "INACTIVE", "MAINTENANCE", "CLOSED"})
        @jakarta.validation.constraints.Pattern(regexp = "ACTIVE|INACTIVE|MAINTENANCE|CLOSED")
        String buildingStatus,
        @Schema(description = "Họ tên bên A (chủ nhà/đại diện cho thuê)", example = "Nguyễn Văn A") @Size(max = 200) String lessorName,
        @Schema(description = "Ngày sinh bên A", example = "1980-01-01") @Past LocalDate lessorDateOfBirth,
        @Schema(description = "Số CCCD/CMND bên A (9 hoặc 12 số)", example = "079123456789")
        @Pattern(regexp = "\\d{9}|\\d{12}", message = "Số CCCD/CMND phải gồm 9 hoặc 12 chữ số") String lessorIdNumber,
        @Schema(description = "Ngày cấp CCCD bên A", example = "2021-05-20") @PastOrPresent LocalDate lessorIdIssueDate,
        @Schema(description = "Nơi cấp CCCD bên A", example = "Cục Cảnh sát QLHC về TTXH") @Size(max = 255) String lessorIdIssuePlace,
        @Schema(description = "Số điện thoại bên A", example = "0901234567")
        @Pattern(regexp = "^(0|\\+84)[0-9]{9,10}$", message = "Số điện thoại không hợp lệ") String lessorPhone,
        @Schema(description = "Địa chỉ thường trú bên A") @Size(max = 500) String lessorAddress
) {
    public BuildingRequest(String name, String address, Long wardId, Long provinceId,
                           BigDecimal latitude, BigDecimal longitude, String description) {
        this(name, address, wardId, provinceId, latitude, longitude, description, null,
                null, null, null, null, null, null, null);
    }

    public BuildingRequest(String name, String address, Long wardId, Long provinceId,
                           BigDecimal latitude, BigDecimal longitude, String description,
                           String buildingStatus) {
        this(name, address, wardId, provinceId, latitude, longitude, description, buildingStatus,
                null, null, null, null, null, null, null);
    }
}
