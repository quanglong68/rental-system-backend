package fptu.semester8.mss301.propertiesservice.features.room.infrastructure.persistence;

import fptu.semester8.mss301.propertiesservice.features.room.domain.Room;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface RoomRepository extends JpaRepository<Room, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Room r where r.id = :id")
    Optional<Room> findByIdForUpdate(Long id);
    boolean existsByBuildingIdAndRoomCode(Long buildingId, String roomCode);

    boolean existsByBuildingIdAndRoomCodeAndIdNot(Long buildingId, String roomCode, Long id);

    Page<Room> findAllByBuildingId(Long buildingId, Pageable pageable);
}
