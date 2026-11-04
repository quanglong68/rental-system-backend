package com.rental.listingbooking.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "room_listing")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomListing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Column(name = "building_id", nullable = false)
    private Long buildingId;

    @Column(name = "building_name")
    private String buildingName;

    @Column(name = "address")
    private String address;

    @Column(name = "ward_id")
    private Long wardId;

    @Column(name = "district_id")
    private Long districtId;

    @Column(name = "room_type_id")
    private Long roomTypeId;

    @Column(name = "room_type_name")
    private String roomTypeName;

    @Column(name = "room_number")
    private String roomNumber;

    @Column(name = "max_capacity")
    private Integer maxCapacity;

    @Column(name = "price", nullable = false)
    private BigDecimal price;

    @Column(name = "latitude")
    private BigDecimal latitude;

    @Column(name = "longitude")
    private BigDecimal longitude;

    @Column(name = "room_status", nullable = false)
    private String roomStatus; // VACANT, UPCOMING_VACANT, OCCUPIED, MAINTENANCE

    @Column(name = "room_status_event_at")
    private Instant roomStatusEventAt;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "deposit_amount")
    private BigDecimal depositAmount;

    @Column(name = "electricity_price")
    private BigDecimal electricityPrice;

    @Column(name = "water_price")
    private BigDecimal waterPrice;

    @Column(name = "water_unit")
    private String waterUnit; // M3, PERSON

    @Column(name = "payment_method")
    private String paymentMethod; // CASH, BANK_TRANSFER

    @Column(name = "payment_due_day")
    private Integer paymentDueDay;

    @Column(name = "min_term_months")
    private Integer minTermMonths;

    @Column(name = "available_from")
    private LocalDate availableFrom;

    @Column(name = "lessor_name")
    private String lessorName;

    @Column(name = "lessor_date_of_birth")
    private LocalDate lessorDateOfBirth;

    @Column(name = "lessor_id_number")
    private String lessorIdNumber;

    @Column(name = "lessor_id_issue_date")
    private LocalDate lessorIdIssueDate;

    @Column(name = "lessor_id_issue_place")
    private String lessorIdIssuePlace;

    @Column(name = "lessor_phone")
    private String lessorPhone;

    @Column(name = "lessor_address")
    private String lessorAddress;

    @Column(name = "status", nullable = false)
    private String status; // ACTIVE, CLOSED

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (status == null) status = "ACTIVE";
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
