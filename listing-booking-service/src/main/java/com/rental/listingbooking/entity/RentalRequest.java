package com.rental.listingbooking.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "rental_request")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RentalRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "listing_id", nullable = false)
    private Long listingId;

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Column(name = "building_id", nullable = false)
    private Long buildingId;

    @Column(name = "status", nullable = false)
    private String status; // PENDING, VERIFIED, ACCEPTED, REJECTED, CANCELLED

    @Column(name = "move_in_date", nullable = false)
    private LocalDate moveInDate;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "full_name", length = 200)
    private String fullName;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "gender", length = 20)
    private String gender;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "permanent_address", length = 500)
    private String permanentAddress;

    @Column(name = "occupation", length = 200)
    private String occupation;

    @Column(name = "cccd_number", length = 20)
    private String cccdNumber;

    @Column(name = "cccd_issue_date")
    private LocalDate cccdIssueDate;

    @Column(name = "cccd_issue_place", length = 255)
    private String cccdIssuePlace;

    @Column(name = "cccd_front_key", length = 500)
    private String cccdFrontKey;

    @Column(name = "cccd_back_key", length = 500)
    private String cccdBackKey;

    @Column(name = "terms_accepted", nullable = false)
    private boolean termsAccepted;

    @Column(name = "rent_amount")
    private BigDecimal rentAmount;

    @Column(name = "deposit_amount")
    private BigDecimal depositAmount;

    @Column(name = "electricity_price")
    private BigDecimal electricityPrice;

    @Column(name = "water_price")
    private BigDecimal waterPrice;

    @Column(name = "water_unit", length = 20)
    private String waterUnit;

    @Column(name = "payment_method", length = 30)
    private String paymentMethod;

    @Column(name = "payment_due_day")
    private Integer paymentDueDay;

    @Column(name = "min_term_months")
    private Integer minTermMonths;

    @Column(name = "available_from")
    private LocalDate availableFrom;

    @Column(name = "lessor_name", length = 200)
    private String lessorName;

    @Column(name = "lessor_date_of_birth")
    private LocalDate lessorDateOfBirth;

    @Column(name = "lessor_id_number", length = 20)
    private String lessorIdNumber;

    @Column(name = "lessor_id_issue_date")
    private LocalDate lessorIdIssueDate;

    @Column(name = "lessor_id_issue_place", length = 255)
    private String lessorIdIssuePlace;

    @Column(name = "lessor_phone", length = 20)
    private String lessorPhone;

    @Column(name = "lessor_address", length = 500)
    private String lessorAddress;

    @Column(name = "reject_reason", columnDefinition = "TEXT")
    private String rejectReason;

    @Column(name = "verified_by")
    private Long verifiedBy;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (requestedAt == null) requestedAt = now;
        if (updatedAt == null) updatedAt = now;
        if (status == null) status = "PENDING";
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
