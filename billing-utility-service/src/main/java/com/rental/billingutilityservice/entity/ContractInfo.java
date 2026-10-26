package com.rental.billingutilityservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Ban sao nho hop dong (docs muc 9.5): hop dong, phong, toa nha, trang thai, ngay bat dau/ket thuc.
 * Cap nhat tu event ContractSigned, ContractRenewed, ContractEnded; dung de kiem tra + phan quyen.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "contract_info")
public class ContractInfo {

    @Id
    @Column(name = "contract_id")
    private Long contractId;

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Column(name = "building_id", nullable = false)
    private Long buildingId;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
