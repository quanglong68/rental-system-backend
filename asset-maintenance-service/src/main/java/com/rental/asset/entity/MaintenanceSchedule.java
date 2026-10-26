package com.rental.asset.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "maintenance_schedule")
@Getter
@Setter
@NoArgsConstructor
public class MaintenanceSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "building_id", nullable = false)
    private Long buildingId;

    @Column(name = "staff_id", nullable = false)
    private Long staffId;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "interval_months", nullable = false)
    private Integer intervalMonths;

    @Column(name = "next_date", nullable = false)
    private LocalDate nextDate;

    @Column(name = "last_done_date")
    private LocalDate lastDoneDate;

    @Column(nullable = false)
    private boolean active = true;
}
