package fptu.semester8.mss301.propertiesservice.features.roomtype.infrastructure;
import fptu.semester8.mss301.propertiesservice.features.roomtype.domain.RoomType; import org.springframework.data.domain.*; import org.springframework.data.jpa.repository.JpaRepository;
public interface RoomTypeRepository extends JpaRepository<RoomType,Long> {
    boolean existsByNameIgnoreCase(String name);

    boolean existsByTypeCodeIgnoreCase(String typeCode);

    boolean existsByTypeCodeIgnoreCaseAndIdNot(String typeCode, Long id);
}
