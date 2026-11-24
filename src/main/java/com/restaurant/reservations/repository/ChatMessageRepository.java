package com.restaurant.reservations.repository;

import com.restaurant.reservations.model.ChatMessage;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    @Query("SELECT m FROM ChatMessage m JOIN FETCH m.sender WHERE m.groupId = :groupId "
        + "AND (:beforeId IS NULL OR m.id < :beforeId) ORDER BY m.id DESC")
    List<ChatMessage> findPage(@Param("groupId") Long groupId, @Param("beforeId") Long beforeId, Pageable pageable);

    @Query("SELECT m FROM ChatMessage m JOIN FETCH m.sender WHERE m.groupId = :groupId AND m.id > :afterId ORDER BY m.id ASC")
    List<ChatMessage> findAfter(@Param("groupId") Long groupId, @Param("afterId") Long afterId, Pageable pageable);

    long countByGroupIdAndIdGreaterThan(Long groupId, Long id);
}
