package com.rental.billingutilityservice.repository;

import com.rental.billingutilityservice.entity.MeterHandover;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MeterHandoverRepository extends JpaRepository<MeterHandover, Long> {
    List<MeterHandover> findByContractIdIn(Collection<Long> contractIds);

    List<MeterHandover> findByRoomId(Long roomId);
}

