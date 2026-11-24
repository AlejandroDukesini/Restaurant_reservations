package com.restaurant.reservations.repository;

import com.restaurant.reservations.model.Notification;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByRecipientIdOrderByIdDesc(Long recipientId, Pageable pageable);

    long countByRecipientIdAndReadAtIsNull(Long recipientId);

    @Modifying
    @Query("UPDATE Notification n SET n.readAt = :now WHERE n.recipientId = :userId AND n.readAt IS NULL "
        + "AND (:id IS NULL OR n.id = :id)")
    int markRead(@Param("userId") Long userId, @Param("id") Long id, @Param("now") LocalDateTime now);
}
