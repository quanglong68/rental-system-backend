package fptu.semester8.mss301.propertiesservice.features.roomoccupant.infrastructure.persistence;

import fptu.semester8.mss301.propertiesservice.features.roomoccupant.application.port.out.RoomOccupantRepository;
import fptu.semester8.mss301.propertiesservice.features.roomoccupant.infrastructure.JpaRoomOccupantRepository;
import fptu.semester8.mss301.propertiesservice.features.roomoccupant.domain.RoomOccupant;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.*;

import java.time.LocalDate;
import java.util.*;

@Repository
@RequiredArgsConstructor
@FieldDefaults(level = lombok.AccessLevel.PRIVATE, makeFinal = true)
public class JpaRoomOccupantPersistence implements RoomOccupantRepository {

    JpaRoomOccupantRepository jpaRoomOccupantRepository;

    public Page<RoomOccupant> current(long roomId, LocalDate date, Pageable p) {
        return jpaRoomOccupantRepository.current(roomId, date, p);
    }

    public Page<RoomOccupant> history(long roomId, Pageable p) {
        return jpaRoomOccupantRepository.findByRoomId(roomId, p);
    }

    public Page<RoomOccupant> byUser(long userId, Pageable p) {
        return jpaRoomOccupantRepository.userHistory(userId, p);
    }

    public Optional<RoomOccupant> byId(long id) {
        return jpaRoomOccupantRepository.findById(id);
    }

    public Optional<RoomOccupant> byAssignment(long id) {
        return jpaRoomOccupantRepository.findByContractTenantId(id);
    }

    public List<RoomOccupant> byContract(long id) {
        return jpaRoomOccupantRepository.findByContractId(id);
    }

    public long countAt(long roomId, LocalDate date) {
        return jpaRoomOccupantRepository.countAt(roomId, date);
    }

    public long countFuture(long roomId, LocalDate date) {
        return jpaRoomOccupantRepository.countFuture(roomId, date);
    }

    public RoomOccupant save(RoomOccupant o) {
        return jpaRoomOccupantRepository.saveAndFlush(o);
    }
}
