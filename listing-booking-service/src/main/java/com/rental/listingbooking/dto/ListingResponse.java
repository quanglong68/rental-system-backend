package com.rental.listingbooking.dto;

import com.rental.listingbooking.entity.RoomListing;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ListingResponse {
    private Long id;
    private Long roomId;
    private Long buildingId;
    private String buildingName;
    private String address;
    private Long wardId;
    private Long districtId;
    private Long roomTypeId;
    private String roomTypeName;
    private String roomNumber;
    private Integer maxCapacity;
    private BigDecimal price;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String roomStatus;
    private String title;
    private String description;
    private BigDecimal depositAmount;
    private BigDecimal electricityPrice;
    private BigDecimal waterPrice;
    private String waterUnit;
    private String paymentMethod;
    private Integer paymentDueDay;
    private Integer minTermMonths;
    private LocalDate availableFrom;
    private String lessorName;
    private String lessorPhone;
    private String lessorAddress;
    private String status;
    private Instant createdAt;
    private Double distanceKm;

    public static ListingResponse from(RoomListing entity) {
        return ListingResponse.builder()
                .id(entity.getId())
                .roomId(entity.getRoomId())
                .buildingId(entity.getBuildingId())
                .buildingName(entity.getBuildingName())
                .address(entity.getAddress())
                .wardId(entity.getWardId())
                .districtId(entity.getDistrictId())
                .roomTypeId(entity.getRoomTypeId())
                .roomTypeName(entity.getRoomTypeName())
                .roomNumber(entity.getRoomNumber())
                .maxCapacity(entity.getMaxCapacity())
                .price(entity.getPrice())
                .latitude(entity.getLatitude())
                .longitude(entity.getLongitude())
                .roomStatus(entity.getRoomStatus())
                .title(entity.getTitle())
                .description(entity.getDescription())
                .depositAmount(entity.getDepositAmount())
                .electricityPrice(entity.getElectricityPrice())
                .waterPrice(entity.getWaterPrice())
                .waterUnit(entity.getWaterUnit())
                .paymentMethod(entity.getPaymentMethod())
                .paymentDueDay(entity.getPaymentDueDay())
                .minTermMonths(entity.getMinTermMonths())
                .availableFrom(entity.getAvailableFrom())
                .lessorName(entity.getLessorName())
                .lessorPhone(entity.getLessorPhone())
                .lessorAddress(entity.getLessorAddress())
                .status(entity.getStatus())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
