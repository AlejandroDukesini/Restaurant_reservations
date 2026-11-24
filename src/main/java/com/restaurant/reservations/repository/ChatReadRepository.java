package com.restaurant.reservations.repository;

import com.restaurant.reservations.model.ChatRead;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatReadRepository extends JpaRepository<ChatRead, Long> {
    Optional<ChatRead> findByGroupIdAndUserId(Long groupId, Long userId);
}
