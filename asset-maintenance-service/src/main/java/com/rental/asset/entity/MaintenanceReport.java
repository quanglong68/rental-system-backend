package com.rental.asset.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "maintenance_report")
@Getter
@Setter
@NoArgsConstructor
public class MaintenanceReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Column(name = "building_id", nullable = false)
    private Long buildingId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @Column(nullable = false)
    private String status = "PENDING";

    @Column(name = "reject_reason", columnDefinition = "text")
    private String rejectReason;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createxdAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "report_image", joinColumns = @JoinColumn(name = "report_id"))
    @Column(name = "image_url", nullable = false)
    private List<String> imageUrls = new ArrayList<>();
}
