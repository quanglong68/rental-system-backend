package fptu.semester8.mss301.propertiesservice.features.roomtype.domain;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.Instant;

@Getter
@Entity
@Table(name = "room_type")
public class RoomType {
    @Id
    @Column(name = "room_type_id")
    private Long id;
    @Column(nullable = false, length = 100)
    private String name;
    @Column(name = "type_code", length = 50)
    private String typeCode;
    @Column(columnDefinition = "TEXT")
    private String description;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected RoomType() {
    }

    public RoomType(Long id, String typeCode, String name, String description, Instant now) {
        this.id = id;
        this.typeCode = typeCode;
        this.name = name;
        this.description = description;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(String typeCode, String name, String description, Instant now) {
        this.typeCode = typeCode;
        this.name = name;
        this.description = description;
        this.updatedAt = now;
    }

}
