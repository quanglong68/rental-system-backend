package com.rental.listingservice.features.listing.infrastructure.persistence.entity;

import com.rental.listingservice.features.listing.domain.enums.ListingStatus;
import com.rental.listingservice.features.listing.domain.enums.RoomStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "room_listing")
public class RoomListing {

    @Id
    @Column(name = "room_listing_id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Column(name = "building_id", nullable = false)
    private Long buildingId;

    @Column(name = "building_name", nullable = false, length = 255)
    private String buildingName;

    @Column(name = "address", nullable = false, length = 500)
    private String address;

    @Column(name = "ward_id")
    private Long wardId;

    @Column(name = "district_id")
    private Long districtId;

    @Column(name = "room_type_id", nullable = false)
    private Long roomTypeId;

    @Column(name = "room_type_name", nullable = false, length = 100)
    private String roomTypeName;

    @Column(name = "room_number", nullable = false, length = 50)
    private String roomNumber;

    @Column(name = "max_capacity", nullable = false)
    private Integer maxCapacity;

    @Column(name = "price", nullable = false, precision = 15, scale = 0)
    private BigDecimal price;

    @Column(name = "latitude", precision = 10, scale = 8)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 11, scale = 8)
    private BigDecimal longitude;

    @Convert(converter = RoomStatusConverter.class)
    @Column(name = "room_status", nullable = false, length = 30)
    private RoomStatus roomStatus;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Convert(converter = ListingStatusConverter.class)
    @Column(name = "status", nullable = false, length = 20)
    private ListingStatus status = ListingStatus.ACTIVE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected RoomListing() {
    }

    public RoomListing(Long id, Long roomId, Long buildingId, String buildingName, String address,
            Long wardId, Long districtId, Long roomTypeId, String roomTypeName, String roomNumber,
            Integer maxCapacity, BigDecimal price, BigDecimal latitude, BigDecimal longitude,
            RoomStatus roomStatus, String title, String description, Instant now) {
        this.id = id;
        this.roomId = roomId;
        this.buildingId = buildingId;
        this.buildingName = buildingName;
        this.address = address;
        this.wardId = wardId;
        this.districtId = districtId;
        this.roomTypeId = roomTypeId;
        this.roomTypeName = roomTypeName;
        this.roomNumber = roomNumber;
        this.maxCapacity = maxCapacity;
        this.price = price;
        this.latitude = latitude;
        this.longitude = longitude;
        this.roomStatus = roomStatus;
        this.title = title;
        this.description = description;
        this.status = ListingStatus.ACTIVE;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void updateContent(String title, String description, Instant now) {
        this.title = title;
        this.description = description;
        this.updatedAt = now;
    }

    public void close(Instant now) {
        this.status = ListingStatus.CLOSED;
        this.updatedAt = now;
    }

    public void syncRoomSnapshot(String buildingName, String address, Long wardId, Long districtId,
            String roomTypeName, String roomNumber, Integer maxCapacity, BigDecimal price,
            BigDecimal latitude, BigDecimal longitude, RoomStatus roomStatus, Instant now) {
        this.buildingName = buildingName;
        this.address = address;
        this.wardId = wardId;
        this.districtId = districtId;
        this.roomTypeName = roomTypeName;
        this.roomNumber = roomNumber;
        this.maxCapacity = maxCapacity;
        this.price = price;
        this.latitude = latitude;
        this.longitude = longitude;
        this.roomStatus = roomStatus;
        this.updatedAt = now;
    }

    public void syncBuildingSnapshot(String buildingName, String address, Long wardId, Long districtId,
            BigDecimal latitude, BigDecimal longitude, Instant now) {
        this.buildingName = buildingName;
        this.address = address;
        this.wardId = wardId;
        this.districtId = districtId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.updatedAt = now;
    }

    public void syncPrice(BigDecimal price, Instant now) {
        this.price = price;
        this.updatedAt = now;
    }

    public Long getId() { return id; }
    public Long getRoomId() { return roomId; }
    public Long getBuildingId() { return buildingId; }
    public String getBuildingName() { return buildingName; }
    public String getAddress() { return address; }
    public Long getWardId() { return wardId; }
    public Long getDistrictId() { return districtId; }
    public Long getRoomTypeId() { return roomTypeId; }
    public String getRoomTypeName() { return roomTypeName; }
    public String getRoomNumber() { return roomNumber; }
    public Integer getMaxCapacity() { return maxCapacity; }
    public BigDecimal getPrice() { return price; }
    public BigDecimal getLatitude() { return latitude; }
    public BigDecimal getLongitude() { return longitude; }
    public RoomStatus getRoomStatus() { return roomStatus; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public ListingStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
