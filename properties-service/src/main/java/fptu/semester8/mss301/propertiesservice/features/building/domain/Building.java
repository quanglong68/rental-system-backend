package fptu.semester8.mss301.propertiesservice.features.building.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

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
    public boolean isDeleted() { return deleted; }
    public Instant getDeletedAt() { return deletedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
