package com.rental.user.entity;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity @Table(name = "staff_building") @Getter @Setter @NoArgsConstructor
public class StaffBuilding {
    @EmbeddedId
    @AttributeOverrides({@AttributeOverride(name = "staffId", column = @Column(name = "staff_id")),
            @AttributeOverride(name = "buildingId", column = @Column(name = "building_id"))})
    private StaffBuildingId id;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt = Instant.now();
    @Column(name = "updated_at", nullable = false) private Instant updatedAt = Instant.now();
    public StaffBuilding(StaffBuildingId id) { this.id = id; }
}
