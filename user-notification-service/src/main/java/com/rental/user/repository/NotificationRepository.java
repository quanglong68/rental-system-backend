package com.rental.user.repository;

import com.rental.user.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    Page<Notification> findByRecipientIdAndReadFalse(Long recipientId, Pageable pageable);
    Page<Notification> findByRecipientId(Long recipientId, Pageable pageable);
    long countByRecipientIdAndReadFalse(Long recipientId);
    @Modifying
    @Query("update Notification n set n.read = true, n.updatedAt = CURRENT_TIMESTAMP "
            + "where n.recipientId = :recipientId and n.read = false")
    int markAllReadByRecipientId(@Param("recipientId") Long recipientId);
}
