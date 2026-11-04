package com.rental.propertiesservice.features.building.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "building")
public class Building {
    @Id
    @Column(name = "building_id", nullable = false, updatable = false)
    private Long buildingId;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "address", nullable = false, length = 500)
    private String address;

    @Column(name = "ward_id", nullable = false)
    private Long wardId;

    @Column(name = "province_id", nullable = false)
    private Long provinceId;

    @Column(name = "latitude", nullable = false, precision = 10, scale = 8)
    private BigDecimal latitude;

    @Column(name = "longitude", nullable = false, precision = 11, scale = 8)
    private BigDecimal longitude;

    @Column(name = "description")
    private String description;

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

    @Column(name = "building_status", nullable = false, length = 20)
    private String buildingStatus = BuildingStatus.ACTIVE.getValue();

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Building() {
    }

    public Building(Long buildingId, String name, String address, Long wardId, Long provinceId,
                    BigDecimal latitude, BigDecimal longitude, String description,
                    Instant now) {
        this.buildingId = buildingId;
        this.name = name;
        this.address = address;
        this.wardId = wardId;
        this.provinceId = provinceId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.description = description;
        this.deleted = false;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(String name, String address, Long wardId, Long provinceId,
                       BigDecimal latitude, BigDecimal longitude,
                       String description, Instant now) {
        this.name = name;
        this.address = address;
        this.wardId = wardId;
        this.provinceId = provinceId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.description = description;
        this.updatedAt = now;
    }

    public void updateLessor(String lessorName, LocalDate lessorDateOfBirth, String lessorIdNumber,
                             LocalDate lessorIdIssueDate, String lessorIdIssuePlace,
                             String lessorPhone, String lessorAddress, Instant now) {
        this.lessorName = lessorName;
        this.lessorDateOfBirth = lessorDateOfBirth;
        this.lessorIdNumber = lessorIdNumber;
        this.lessorIdIssueDate = lessorIdIssueDate;
        this.lessorIdIssuePlace = lessorIdIssuePlace;
        this.lessorPhone = lessorPhone;
        this.lessorAddress = lessorAddress;
        this.updatedAt = now;
    }

    public String getBuildingStatus() { return buildingStatus; }

    public void changeStatus(BuildingStatus status, Instant now) {
        this.buildingStatus = status.getValue();
        this.updatedAt = now;
    }

    public void softDelete(Instant now) {
        this.deleted = true;
        this.deletedAt = now;
        this.updatedAt = now;
    }

    public Long getBuildingId() { return buildingId; }
    public String getName() { return name; }
    public String getAddress() { return address; }
    public Long getWardId() { return wardId; }
    public Long getProvinceId() { return provinceId; }
    public BigDecimal getLatitude() { return latitude; }
    public BigDecimal getLongitude() { return longitude; }
    public String getDescription() { return description; }
    public String getLessorName() { return lessorName; }
    public LocalDate getLessorDateOfBirth() { return lessorDateOfBirth; }
    public String getLessorIdNumber() { return lessorIdNumber; }
    public LocalDate getLessorIdIssueDate() { return lessorIdIssueDate; }
    public String getLessorIdIssuePlace() { return lessorIdIssuePlace; }
    public String getLessorPhone() { return lessorPhone; }
    public String getLessorAddress() { return lessorAddress; }
    public boolean isDeleted() { return deleted; }
    public Instant getDeletedAt() { return deletedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
