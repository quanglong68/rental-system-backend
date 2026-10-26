package com.rental.asset.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "maintenance_record")
@Getter
@Setter
@NoArgsConstructor
public class MaintenanceRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "maintenance_request_id", nullable = false, unique = true)
    private Long maintenanceRequestId;

    @Column(name = "action_taken", nullable = false, columnDefinition = "text")
    private String actionTaken;

    @Column(nullable = false)
    private BigDecimal cost;

    @Column(nullable = false)
    private OffsetDateTime date = OffsetDateTime.now(ZoneOffset.UTC);

    @Column(name = "completed_by", nullable = false)
    private Long completedBy;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "maintenance_request_id", referencedColumnName = "id", insertable = false, updatable = false)
    @JsonIgnore
    private MaintenanceRequest request;
}
