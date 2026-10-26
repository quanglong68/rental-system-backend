package com.rental.billingutilityservice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Dong ho theo phong + loai tien ich (docs muc 9.5).
 * Moi phong chi co mot dong ho dang hoat dong cho moi loai tien ich.
 * buildingId lay tu Property qua Feign #6 khi dang ky (khong FK xuyen service).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "meter")
public class Meter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Column(name = "building_id", nullable = false)
    private Long buildingId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "utility_type_id", nullable = false)
    private UtilityType utilityType;

    @Column(name = "meter_code", length = 50)
    private String meterCode;

    @Column(name = "initial_index", nullable = false, precision = 15, scale = 2)
    private BigDecimal initialIndex = BigDecimal.ZERO;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
