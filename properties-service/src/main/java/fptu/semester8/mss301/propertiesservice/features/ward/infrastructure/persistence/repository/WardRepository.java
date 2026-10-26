package fptu.semester8.mss301.propertiesservice.features.ward.infrastructure.persistence.repository;

import fptu.semester8.mss301.propertiesservice.features.ward.domain.Ward;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WardRepository extends JpaRepository<Ward, Long> {

    boolean existsByWardId(Long wardId);

    List<Ward> findWardsByProvinceId(Long provinceId);
}
