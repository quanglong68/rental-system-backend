package fptu.semester8.mss301.propertiesservice.features.roomprice.infrastructure;

import fptu.semester8.mss301.propertiesservice.features.roomprice.domain.RoomPrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RoomPriceRepository extends JpaRepository<RoomPrice, Long> {
    List<RoomPrice> findAllByRoomIdOrderByEffectiveFromDesc(Long roomId);

    Optional<RoomPrice> findFirstByRoomIdAndEffectiveFromAfterOrderByEffectiveFromAsc(Long roomId, LocalDate date);

    @Query("select p from RoomPrice p where p.roomId = :roomId and p.effectiveFrom <= :date and (p.effectiveTo is null or p.effectiveTo >= :date)")
    Optional<RoomPrice> findCoveringDate(@Param("roomId") Long roomId, @Param("date") LocalDate date);

}
