package com.rental.contracttenant.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "contract")
public class Contract {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** NULL for direct manual drafts; set for event-driven drafts. UNIQUE prevents duplicate events. */
    @Column(name = "rental_request_id", unique = true)
    private Long rentalRequestId;

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Column(name = "building_id", nullable = false)
    private Long buildingId;

    /** Original customer from RentalRequestAccepted, retained until signing creates ContractTenant. */
    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ContractStatus status;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "rent_amount", nullable = false, precision = 15, scale = 0)
    private BigDecimal rentAmount;

    @Column(name = "deposit_amount", precision = 15, scale = 0)
    private BigDecimal depositAmount;

    @Column(name = "termination_reason", length = 1000)
    private String terminationReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "signature_status", nullable = false, length = 30)
    private SignatureStatus signatureStatus = SignatureStatus.NOT_READY;

    @Column(name = "signature_deadline")
    private Instant signatureDeadline;

    @Column(name = "renewed_from_contract_id")
    private Long renewedFromContractId;

    /** Set only when this draft was produced from an approved renewal request. */
    @Column(name = "renewal_request_id")
    private Long renewalRequestId;

    @Column(name = "room_number", length = 50)
    private String roomNumber;

    @Column(name = "building_name", length = 255)
    private String buildingName;

    @Column(name = "room_address", length = 500)
    private String roomAddress;

    @Column(name = "electricity_price", precision = 15, scale = 0)
    private BigDecimal electricityPrice;

    @Column(name = "water_price", precision = 15, scale = 0)
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

    @Column(name = "tenant_full_name", length = 200)
    private String tenantFullName;

    @Column(name = "tenant_date_of_birth")
    private LocalDate tenantDateOfBirth;

    @Column(name = "tenant_gender", length = 20)
    private String tenantGender;

    @Column(name = "tenant_phone", length = 20)
    private String tenantPhone;

    @Column(name = "tenant_email", length = 255)
    private String tenantEmail;

    @Column(name = "tenant_permanent_address", length = 500)
    private String tenantPermanentAddress;

    @Column(name = "tenant_occupation", length = 200)
    private String tenantOccupation;

    @Column(name = "tenant_cccd_number", length = 20)
    private String tenantCccdNumber;

    @Column(name = "tenant_cccd_issue_date")
    private LocalDate tenantCccdIssueDate;

    @Column(name = "tenant_cccd_issue_place", length = 255)
    private String tenantCccdIssuePlace;

    @Column(name = "tenant_cccd_front_key", length = 500)
    private String tenantCccdFrontKey;

    @Column(name = "tenant_cccd_back_key", length = 500)
    private String tenantCccdBackKey;

    /**
     * Monotonic revision of the event stream of this contract. Every published contract event carries
     * the revision it produced, and the Property projection only accepts {@code revision == previous + 1}.
     * Bumped under the contract row lock, in the same transaction that writes the outbox row.
     */
    @Column(nullable = false)
    private long revision;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
