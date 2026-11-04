package com.rental.contracttenant.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Occupant of a contract. Rows are historical: leaving sets {@link #leftAt} instead of deleting the
 * row, so Billing can prorate by day. {@code contractId} is an intra-service reference with a real
 * foreign key; {@code customerId} is a scalar cross-service reference to the User service and
 * deliberately has no database foreign key.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "contract_tenant")
public class ContractTenant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "contract_id", nullable = false)
    private Long contractId;

    /** Cross-service reference to the customer; the User service owns that row. */
    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "joined_at", nullable = false)
    private LocalDate joinedAt;

    /** Null while the tenant still occupies the room. */
    @Column(name = "left_at")
    private LocalDate leftAt;

    @Column(name = "is_representative", nullable = false)
    private boolean isRepresentative;

    @Column(name = "full_name", length = 200)
    private String fullName;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(length = 20)
    private String gender;

    @Column(length = 20)
    private String phone;

    @Column(length = 255)
    private String email;

    @Column(name = "permanent_address", length = 500)
    private String permanentAddress;

    @Column(length = 200)
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

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
