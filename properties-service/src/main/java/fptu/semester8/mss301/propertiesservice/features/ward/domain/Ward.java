package fptu.semester8.mss301.propertiesservice.features.ward.domain;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.Instant;

@Getter
@Entity
@Table(name = "ward")
public class Ward {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ward_id")
    private Long wardId;

    @Column(name = "province_id", nullable = false)
    private Long provinceId;

    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "division_type", length = 100)
    private String divisionType;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Ward() {
    }

    public Ward(Long p, String c, String n, String d) {
        provinceId = p;
        code = c;
        name = n;
        divisionType = d;
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    public void update(Long p, String c, String n, String d) {
        provinceId = p;
        code = c;
        name = n;
        divisionType = d;
        updatedAt = Instant.now();
    }

}
