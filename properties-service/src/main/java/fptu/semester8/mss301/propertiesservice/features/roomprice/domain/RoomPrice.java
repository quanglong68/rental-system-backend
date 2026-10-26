package fptu.semester8.mss301.propertiesservice.features.roomprice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Getter
@Entity
@Table(name = "room_price")
public class RoomPrice {
    @Id
    @Column(name = "room_price_id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "room_id", nullable = false, updatable = false)
    private Long roomId;

    @Column(nullable = false, precision = 15, scale = 0)
    private BigDecimal price;

    @Column(name = "effective_from", nullable = false, updatable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected RoomPrice() {
    }

    public RoomPrice(Long id, Long roomId, BigDecimal price, LocalDate effectiveFrom,
                     LocalDate effectiveTo, Instant now) {
        this.id = id;
        this.roomId = roomId;
        this.price = price;
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void closeOn(LocalDate effectiveTo, Instant now) {
        this.effectiveTo = effectiveTo;
        this.updatedAt = now;
    }

    public void changePrice(BigDecimal price, Instant now) {
        this.price = price;
        this.updatedAt = now;
    }

}
