package com.rental.asset.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "appliance")
@Getter
@Setter
@NoArgsConstructor
public class Appliance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Column(name = "building_id", nullable = false)
    private Long buildingId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String status = "GOOD";

    @Column(name = "warranty_provider")
    private String warrantyProvider;

    @Column(name = "warranty_start_date")
    private LocalDate warrantyStartDate;

    @Column(name = "warranty_end_date")
    private LocalDate warrantyEndDate;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    public boolean isWarrantyActive() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        return warrantyStartDate != null
                && warrantyEndDate != null
                && !today.isBefore(warrantyStartDate)
                && !today.isAfter(warrantyEndDate);
    }
}
