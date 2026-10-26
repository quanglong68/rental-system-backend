package fptu.semester8.mss301.propertiesservice.features.room.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "room", uniqueConstraints = @UniqueConstraint(name = "uq_room_building_room_number", columnNames = {"building_id", "room_number"}))
public class Room {
    @Id
    @Column(name = "room_id")
    private Long id;

    @Column(name = "building_id", nullable = false)
    private Long buildingId;

    @Column(name = "room_type_id", nullable = false)
    private Long roomTypeId;

    @Column(name = "room_number", nullable = false, length = 50)
    private String roomCode;

    @Column(name = "max_capacity", nullable = false)
    private Integer maxCapacity;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(precision = 10, scale = 2)
    private BigDecimal area;

    @Column(name = "bedroom_count")
    private Integer bedroomCount;

    @Column(name = "bathroom_count")
    private Integer bathroomCount;

    @Column(name = "base_price", precision = 15, scale = 0)
    private BigDecimal basePrice;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Room() {
    }

    public Room(Long id, Long buildingId, Long roomTypeId, String roomCode, Integer maxCapacity, String status, BigDecimal area, Integer bedroomCount, Integer bathroomCount, BigDecimal basePrice, Instant now) {
        this.id = id;
        this.buildingId = buildingId;
        this.roomTypeId = roomTypeId;
        this.roomCode = roomCode;
        this.maxCapacity = maxCapacity;
        this.status = status;
        this.area = area;
        this.bedroomCount = bedroomCount;
        this.bathroomCount = bathroomCount;
        this.basePrice = basePrice;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(Long roomTypeId, String roomCode, Integer maxCapacity, String status, BigDecimal area, Integer bedroomCount, Integer bathroomCount, BigDecimal basePrice, Instant now) {
        this.roomTypeId = roomTypeId;
        this.roomCode = roomCode;
        this.maxCapacity = maxCapacity;
        this.status = status;
        this.area = area;
        this.bedroomCount = bedroomCount;
        this.bathroomCount = bathroomCount;
        this.basePrice = basePrice;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public Long getBuildingId() {
        return buildingId;
    }

    public Long getRoomTypeId() {
        return roomTypeId;
    }

    public String getRoomCode() {
        return roomCode;
    }

    public Integer getMaxCapacity() {
        return maxCapacity;
    }

    public String getStatus() {
        return status;
    }

    public BigDecimal getArea() {
        return area;
    }

    public Integer getBedroomCount() {
        return bedroomCount;
    }

    public Integer getBathroomCount() {
        return bathroomCount;
    }

    public BigDecimal getBasePrice() {
        return basePrice;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
