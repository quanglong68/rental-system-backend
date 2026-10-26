package com.rental.billingutilityservice.entity;

import com.rental.billingutilityservice.enums.UsageType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * Dich vu gan hop dong (docs muc 9.5): SHARED chia theo dau nguoi, PERSONAL ai dang ky
 * nguoi do tra (bat buoc co contractTenantId).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "contract_service")
public class ContractService {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "contract_id", nullable = false)
    private Long contractId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id", nullable = false)
    private BuildingService service;

    @Column(name = "service_name", nullable = false, length = 200)
    private String serviceName;

    @Column(nullable = false)
    private Integer quantity = 1;

    @Enumerated(EnumType.STRING)
    @Column(name = "usage_type", nullable = false, length = 20)
    private UsageType usageType;

    @Column(name = "contract_tenant_id")
    private Long contractTenantId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
