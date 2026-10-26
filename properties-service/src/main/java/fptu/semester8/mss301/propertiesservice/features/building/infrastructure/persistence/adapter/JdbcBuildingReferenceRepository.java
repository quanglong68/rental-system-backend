package fptu.semester8.mss301.propertiesservice.features.building.infrastructure.persistence.adapter;

import fptu.semester8.mss301.propertiesservice.features.building.application.port.out.BuildingReferenceRepository;
import fptu.semester8.mss301.propertiesservice.features.province.infrastructure.persistence.repository.ProvinceRepository;
import fptu.semester8.mss301.propertiesservice.features.ward.infrastructure.persistence.repository.WardRepository;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
@FieldDefaults(level = lombok.AccessLevel.PRIVATE, makeFinal = true)
public class JdbcBuildingReferenceRepository implements BuildingReferenceRepository {

    JdbcTemplate jdbcTemplate;

    java.time.Clock clock;

    ProvinceRepository provinceRepository;

    WardRepository wardRepository;

    @Override
    public boolean wardExists(long wardId) {
        Boolean exists = jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM ward WHERE ward_id = ?)",
                Boolean.class,
                wardId
        );
        return Boolean.TRUE.equals(exists);
    }

    @Override
    public boolean hasRooms(long buildingId) {
        Boolean exists = jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM room WHERE building_id = ?)",
                Boolean.class,
                buildingId
        );
        return Boolean.TRUE.equals(exists);
    }

    @Override
    public void lockBuilding(long buildingId) {
        jdbcTemplate.query("SELECT building_id FROM building WHERE building_id = ? FOR UPDATE",
                (rs, row) -> rs.getLong(1), buildingId);
    }

    @Override
    public boolean hasResidents(long buildingId) {
        // Departure dates are inclusive, matching the occupant read model.
        // Legacy occupied rooms without occupant history cannot be assumed empty.
        var today = java.time.LocalDate.now(clock);
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM room r WHERE r.building_id = ? AND (
                        EXISTS (SELECT 1 FROM room_occupant o WHERE o.room_id = r.room_id
                            AND o.move_in_date <= ?
                            AND (o.move_out_date IS NULL OR o.move_out_date >= ?)
                            AND (o.room_occupant_status = 'ACTIVE'
                                OR (o.room_occupant_status = 'ENDED' AND o.move_out_date IS NOT NULL)))
                        OR (r.status IN ('OCCUPIED', 'UPCOMING_VACANT')
                            AND NOT EXISTS (SELECT 1 FROM room_occupant o WHERE o.room_id = r.room_id))
                    )
                )
                """, Boolean.class, buildingId, today, today));
    }

    @Override
    public boolean hasOccupiedRooms(long buildingId) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM room WHERE building_id = ? AND status IN ('OCCUPIED', 'UPCOMING_VACANT'))",
                Boolean.class, buildingId));
    }

    @Override
    public boolean buildingExists(long buildingId) {
        Boolean exists = jdbcTemplate.queryForObject("SELECT EXISTS (SELECT 1 FROM building WHERE building_id = ? AND is_deleted = false)", Boolean.class, buildingId);
        return Boolean.TRUE.equals(exists);
    }

    @Override
    public boolean existProvinceById(long provinceId) {
        return provinceRepository.existsByProvinceId(provinceId);
    }

    @Override
    public boolean existWardById(long wardId) {
        return wardRepository.existsByWardId(wardId);
    }

    @Override
    public boolean wardBelongsToProvince(long wardId, long provinceId) {
        Boolean matches = jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM ward WHERE ward_id = ? AND province_id = ?)",
                Boolean.class, wardId, provinceId);
        return Boolean.TRUE.equals(matches);
    }
}
