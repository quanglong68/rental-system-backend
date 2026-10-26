package fptu.semester8.mss301.propertiesservice.features.province.infrastructure.persistence.repository;

import fptu.semester8.mss301.propertiesservice.features.province.domain.Province;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProvinceRepository extends JpaRepository<Province, Long> {
    boolean existsByCodeAndProvinceIdNot(String code, Long id);

    boolean existsByProvinceId(Long provinceId);
}
