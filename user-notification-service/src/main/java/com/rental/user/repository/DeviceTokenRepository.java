package com.rental.user.repository;

import com.rental.user.entity.DeviceToken;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceTokenRepository extends JpaRepository<DeviceToken, Long> {
    Optional<DeviceToken> findByToken(String token);
    List<DeviceToken> findByAccountId(Long accountId);
    void deleteByAccountIdAndToken(Long accountId, String token);
}
