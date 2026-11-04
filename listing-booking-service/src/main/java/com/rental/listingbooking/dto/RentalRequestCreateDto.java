package com.rental.listingbooking.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Hồ sơ thuê bắt buộc trước khi gửi yêu cầu. Điều khoản tài chính được snapshot từ tin đăng,
 * khách chỉ cần xác nhận đã đọc.
 */
public record RentalRequestCreateDto(
    @NotNull(message = "ID tin đăng không được để trống")
    Long listingId,
    @NotBlank(message = "Vui lòng nhập họ tên")
    @Size(max = 200)
    String fullName,
    @NotNull(message = "Vui lòng nhập ngày sinh")
    @Past(message = "Ngày sinh phải ở quá khứ")
    LocalDate dateOfBirth,
    @NotBlank(message = "Vui lòng chọn giới tính")
    String gender,
    @NotBlank(message = "Vui lòng nhập số điện thoại")
    @Pattern(regexp = "^(0|\\+84)[0-9]{9,10}$", message = "Số điện thoại không hợp lệ")
    String phone,
    @Email(message = "Email không hợp lệ")
    @Size(max = 255)
    String email,
    @NotBlank(message = "Vui lòng nhập địa chỉ thường trú")
    @Size(max = 500)
    String permanentAddress,
    @Size(max = 200)
    String occupation,
    @NotBlank(message = "Vui lòng nhập số CCCD")
    @Pattern(regexp = "\\d{12}", message = "Số CCCD phải gồm đúng 12 chữ số")
    String cccdNumber,
    @NotNull(message = "Vui lòng nhập ngày cấp CCCD")
    LocalDate cccdIssueDate,
    @NotBlank(message = "Vui lòng nhập nơi cấp CCCD")
    @Size(max = 255)
    String cccdIssuePlace,
    @NotBlank(message = "Vui lòng tải ảnh CCCD mặt trước")
    String cccdFrontKey,
    @NotBlank(message = "Vui lòng tải ảnh CCCD mặt sau")
    String cccdBackKey,
    @NotNull(message = "Vui lòng chọn ngày dự kiến vào ở")
    LocalDate moveInDate,
    String note,
    boolean termsAccepted
) {}
