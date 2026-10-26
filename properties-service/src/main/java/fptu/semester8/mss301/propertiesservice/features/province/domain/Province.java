package fptu.semester8.mss301.propertiesservice.features.province.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "province")
public class Province {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "province_id")
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

    protected Province() {
    }

    public Province(String code, String name, String divisionType) {
        this.code = code;
        this.name = name;
        this.divisionType = divisionType;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public void update(String code, String name, String divisionType) {
        this.code = code;
        this.name = name;
        this.divisionType = divisionType;
        this.updatedAt = Instant.now();
    }

    public Long getProvinceId() {
        return provinceId;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getDivisionType() {
        return divisionType;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
