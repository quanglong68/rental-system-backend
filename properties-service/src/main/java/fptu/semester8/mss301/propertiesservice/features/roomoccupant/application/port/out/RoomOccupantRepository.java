package fptu.semester8.mss301.propertiesservice.features.roomoccupant.application.port.out;

import fptu.semester8.mss301.propertiesservice.features.roomoccupant.domain.RoomOccupant;
import org.springframework.data.domain.*;

import java.time.LocalDate;
import java.util.*;

public interface RoomOccupantRepository {
    Page<RoomOccupant> current(long roomId, LocalDate date, Pageable pageable);

    Page<RoomOccupant> history(long roomId, Pageable pageable);

    Page<RoomOccupant> byUser(long userId, Pageable pageable);

    Optional<RoomOccupant> byId(long id);

    Optional<RoomOccupant> byAssignment(long assignmentId);

    List<RoomOccupant> byContract(long contractId);

    long countAt(long roomId, LocalDate date);

    long countFuture(long roomId, LocalDate date);

    RoomOccupant save(RoomOccupant occupant);
}
