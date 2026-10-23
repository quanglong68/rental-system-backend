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

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
