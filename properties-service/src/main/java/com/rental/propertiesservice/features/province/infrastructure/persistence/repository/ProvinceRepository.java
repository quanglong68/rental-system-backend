package com.rental.propertiesservice.features.province.infrastructure.persistence.repository;

import com.rental.propertiesservice.features.province.domain.Province;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProvinceRepository extends JpaRepository<Province, Long> {
    boolean existsByCodeAndProvinceIdNot(String code, Long id);

    boolean existsByProvinceId(Long provinceId);
}
