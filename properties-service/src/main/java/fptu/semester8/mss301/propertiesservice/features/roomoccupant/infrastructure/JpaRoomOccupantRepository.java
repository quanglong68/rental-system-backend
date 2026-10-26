package fptu.semester8.mss301.propertiesservice.features.roomoccupant.infrastructure;

import fptu.semester8.mss301.propertiesservice.features.roomoccupant.domain.RoomOccupant;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.*;

@Repository
public interface JpaRoomOccupantRepository extends JpaRepository<RoomOccupant, Long> {
    // ENDED rows count through their inclusive effective departure date, including history queries.
    String EFFECTIVE = "o.moveInDate <= :date and (o.moveOutDate is null or o.moveOutDate >= :date) "
            + "and (o.status = fptu.semester8.mss301.propertiesservice.features.roomoccupant.domain.RoomOccupantStatus.ACTIVE "
            + "or (o.status = fptu.semester8.mss301.propertiesservice.features.roomoccupant.domain.RoomOccupantStatus.ENDED and o.moveOutDate is not null))";

    @Query("select o from RoomOccupant o where o.roomId = :roomId and " + EFFECTIVE)
    Page<RoomOccupant> current(long roomId, LocalDate date, Pageable pageable);

    Page<RoomOccupant> findByRoomId(long roomId, Pageable pageable);

    @Query(value = """
            SELECT o.* FROM room_occupant o JOIN room r ON r.room_id=o.room_id
            JOIN building b ON b.building_id=r.building_id
            WHERE o.user_id=:userId AND b.is_deleted=false
            ORDER BY o.move_in_date DESC, o.room_occupant_id DESC
            """, countQuery = """
            SELECT count(*) FROM room_occupant o JOIN room r ON r.room_id=o.room_id
            JOIN building b ON b.building_id=r.building_id WHERE o.user_id=:userId AND b.is_deleted=false
            """, nativeQuery = true)
    Page<RoomOccupant> userHistory(long userId, Pageable pageable);

    Optional<RoomOccupant> findByContractTenantId(long assignmentId);

    List<RoomOccupant> findByContractId(long contractId);

    @Query("select count(o) from RoomOccupant o where o.roomId = :roomId and " + EFFECTIVE)
    long countAt(long roomId, LocalDate date);

    @Query("select count(o) from RoomOccupant o where o.roomId = :roomId and o.moveInDate > :date "
            + "and (o.status = fptu.semester8.mss301.propertiesservice.features.roomoccupant.domain.RoomOccupantStatus.ACTIVE or o.moveOutDate is not null)")
    long countFuture(long roomId, LocalDate date);
}
