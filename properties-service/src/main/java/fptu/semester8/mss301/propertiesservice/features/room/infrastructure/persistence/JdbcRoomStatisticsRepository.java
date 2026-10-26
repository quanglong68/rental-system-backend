package fptu.semester8.mss301.propertiesservice.features.room.infrastructure.persistence;

import fptu.semester8.mss301.propertiesservice.features.room.application.model.RoomStatistics;
import fptu.semester8.mss301.propertiesservice.features.room.application.port.out.RoomStatisticsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;

@Repository
@RequiredArgsConstructor
public class JdbcRoomStatisticsRepository implements RoomStatisticsRepository {
    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public RoomStatistics summarize(Long buildingId, LocalDate date) {
        // Keep effective dates aligned with JpaRoomOccupantRepository.EFFECTIVE.
        String sql = """
                WITH room_counts AS (
                    SELECT r.*, count(DISTINCT o.user_id) AS occupants
                    FROM room r JOIN building b ON b.building_id = r.building_id
                    LEFT JOIN room_occupant o ON o.room_id = r.room_id
                        AND o.move_in_date <= :date
                        AND (o.move_out_date IS NULL OR o.move_out_date >= :date)
                        AND (o.room_occupant_status = 'ACTIVE'
                            OR (o.room_occupant_status = 'ENDED' AND o.move_out_date IS NOT NULL))
                    WHERE b.is_deleted = false
                """ + (buildingId == null ? "" : " AND r.building_id = :buildingId\n") + """
                    GROUP BY r.room_id
                )
                SELECT count(*) AS total,
                    count(*) FILTER (WHERE occupants > 1) AS operating,
                    count(*) FILTER (WHERE
                        (occupants > 0 AND status IN ('VACANT', 'MAINTENANCE'))
                        OR (occupants = 0 AND status IN ('OCCUPIED', 'UPCOMING_VACANT'))
                        OR occupants > max_capacity
                        OR room_number IS NULL OR btrim(room_number) = ''
                        OR max_capacity IS NULL OR max_capacity < 1
                        OR area IS NULL OR area <= 0
                        OR bedroom_count IS NULL OR bedroom_count < 0
                        OR bathroom_count IS NULL OR bathroom_count < 0
                        OR base_price IS NULL OR base_price < 0
                        OR status IS NULL OR status NOT IN ('VACANT', 'OCCUPIED', 'UPCOMING_VACANT', 'MAINTENANCE')
                    ) AS needs_update
                FROM room_counts
                """;
        var parameters = new MapSqlParameterSource("date", date).addValue("buildingId", buildingId);
        return jdbc.queryForObject(sql, parameters, (rs, row) -> new RoomStatistics(
                rs.getLong("total"), rs.getLong("operating"), rs.getLong("needs_update"), date));
    }
}
