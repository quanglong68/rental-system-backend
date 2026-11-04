package com.rental.listingbooking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Hồ sơ tối thiểu khi xin vào ở ghép: họ tên, SĐT và số CCCD để đưa vào hợp đồng. */
public record RoommateApplicationCreateDto(
    @NotBlank(message = "Họ tên không được để trống")
    @Size(max = 200, message = "Họ tên tối đa 200 ký tự")
    String fullName,

    @NotBlank(message = "Số điện thoại không được để trống")
    @Pattern(regexp = "^0\\d{9,10}$", message = "Số điện thoại không hợp lệ")
    String phone,

    @NotBlank(message = "Số CCCD không được để trống")
    @Pattern(regexp = "^\\d{9}$|^\\d{12}$", message = "Số CCCD phải gồm 9 hoặc 12 chữ số")
    String cccdNumber,

    @NotBlank(message = "Lời nhắn không được để trống")
    String message,

    java.time.LocalDate dateOfBirth,
    @Size(max = 30, message = "Giới tính không hợp lệ") String gender,
    java.time.LocalDate cccdIssueDate,
    @Size(max = 255, message = "Nơi cấp tối đa 255 ký tự") String cccdIssuePlace,
    @Size(max = 1000, message = "Địa chỉ tối đa 1000 ký tự") String permanentAddress,
    @Size(max = 200, message = "Nghề nghiệp tối đa 200 ký tự") String occupation,
    java.time.LocalDate moveInDate,
    @NotBlank(message = "Vui lòng tải ảnh mặt trước CCCD") String cccdFrontKey,
    @NotBlank(message = "Vui lòng tải ảnh mặt sau CCCD") String cccdBackKey
) {}
