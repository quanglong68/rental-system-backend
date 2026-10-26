package fptu.semester8.mss301.propertiesservice.features.roomoccupant.infrastructure.persistence;

import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.*;

@Repository
@RequiredArgsConstructor
@FieldDefaults(level = lombok.AccessLevel.PRIVATE, makeFinal = true)
public class ProjectionCheckpointRepository {

    JdbcTemplate jdbc;

    public record Checkpoint(long contractId, long roomId, long revision, LocalDate signedStartDate,
                             LocalDate approvedMoveOutDate, LocalDate endedDate, boolean nextContractExists) {}

    public Checkpoint lock(long contractId, long roomId) {
        jdbc.update("""
                INSERT INTO occupant_projection_checkpoint(contract_id,room_id) VALUES (?,?)
                ON CONFLICT (contract_id) DO NOTHING
                """, contractId, roomId);
        return jdbc.queryForObject("SELECT * FROM occupant_projection_checkpoint WHERE contract_id=? FOR UPDATE",
                (rs, row) -> map(rs), contractId);
    }

    public List<Checkpoint> forRoom(long roomId) {
        return jdbc.query("SELECT * FROM occupant_projection_checkpoint WHERE room_id=?", (rs, row) -> map(rs), roomId);
    }

    public List<Long> roomIdsAfter(long after, int limit) {
        return jdbc.queryForList("""
                SELECT DISTINCT room_id FROM occupant_projection_checkpoint WHERE room_id>?
                ORDER BY room_id LIMIT ?
                """, Long.class, after, limit);
    }

    public void advance(Checkpoint previous, long revision, UUID eventId, String type, LocalDate date, boolean next) {
        LocalDate start = type.equals("ContractSigned") ? date : previous.signedStartDate();
        LocalDate moveOut = type.equals("MoveOutApproved") ? date : previous.approvedMoveOutDate();
        if (type.equals("ContractRenewed")) moveOut = null;
        LocalDate end = type.equals("ContractEnded") ? date : previous.endedDate();
        jdbc.update("""
                UPDATE occupant_projection_checkpoint SET source_revision=?,last_event_id=?,signed_start_date=?,
                    approved_move_out_date=?,ended_date=?,next_contract_exists=?,updated_at=CURRENT_TIMESTAMP
                WHERE contract_id=?
                """, revision, eventId, start, moveOut, end,
                type.equals("ContractEnded") ? next : previous.nextContractExists(), previous.contractId());
    }

    private static Checkpoint map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Checkpoint(rs.getLong("contract_id"), rs.getLong("room_id"), rs.getLong("source_revision"),
                rs.getObject("signed_start_date", LocalDate.class), rs.getObject("approved_move_out_date", LocalDate.class),
                rs.getObject("ended_date", LocalDate.class), rs.getBoolean("next_contract_exists"));
    }
}
