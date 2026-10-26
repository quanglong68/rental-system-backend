package fptu.semester8.mss301.propertiesservice.features.roomoccupant.domain;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.Instant;
import java.time.LocalDate;

@Getter
@Entity
@Table(name = "room_occupant")
public class RoomOccupant {
    @Id
    @Column(name = "room_occupant_id")
    private Long id;

    @Column(name = "contract_tenant_id")
    private Long contractTenantId;

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "contract_id")
    private Long contractId;

    @Column(name = "move_in_date", nullable = false)
    private LocalDate moveInDate;

    @Column(name = "move_out_date")
    private LocalDate moveOutDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "room_occupant_status", nullable = false, length = 20)
    private RoomOccupantStatus status;

    @Column(name = "source_revision")
    private Long sourceRevision;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;


    protected RoomOccupant() {
    }

    public RoomOccupant(long id, long assignmentId, long roomId, long contractId, long userId,
                        LocalDate joinedAt, long revision, Instant now) {
        this.id = id;
        this.contractTenantId = assignmentId;
        this.roomId = roomId;
        this.contractId = contractId;
        this.userId = userId;
        this.moveInDate = joinedAt;
        this.sourceRevision = revision;
        this.status = RoomOccupantStatus.ACTIVE;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void recordRevision(long revision, Instant now) {
        this.sourceRevision = revision;
        this.updatedAt = now;
    }

    public void end(LocalDate leftAt, long revision, Instant now) {
        if (leftAt.isBefore(moveInDate)) throw new IllegalArgumentException("Departure precedes arrival");
        this.moveOutDate = leftAt;
        this.status = RoomOccupantStatus.ENDED;
        this.sourceRevision = revision;
        this.updatedAt = now;
    }
}
