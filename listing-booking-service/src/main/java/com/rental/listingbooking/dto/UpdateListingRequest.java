package com.rental.listingbooking.dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.LocalDate;

public record UpdateListingRequest(
    @NotBlank(message = "Tiêu đề không được để trống")
    String title,
    String description,
    @PositiveOrZero(message = "Tiền cọc không được âm")
    BigDecimal depositAmount,
    @PositiveOrZero(message = "Giá điện không được âm")
    BigDecimal electricityPrice,
    @PositiveOrZero(message = "Giá nước không được âm")
    BigDecimal waterPrice,
    @Pattern(regexp = "M3|PERSON", message = "Đơn vị nước phải là M3 hoặc PERSON")
    String waterUnit,
    @Pattern(regexp = "CASH|BANK_TRANSFER", message = "Hình thức thanh toán phải là CASH hoặc BANK_TRANSFER")
    String paymentMethod,
    @Min(value = 1, message = "Ngày đến hạn phải từ 1 đến 28")
    @Max(value = 28, message = "Ngày đến hạn phải từ 1 đến 28")
    Integer paymentDueDay,
    @Positive(message = "Kỳ hạn tối thiểu phải lớn hơn 0")
    Integer minTermMonths,
    @FutureOrPresent(message = "Ngày có thể chuyển vào không được ở quá khứ")
    LocalDate availableFrom
) {}
