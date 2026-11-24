package com.restaurant.reservations.repository;

import com.restaurant.reservations.model.OrderEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderEventRepository extends JpaRepository<OrderEvent, Long> {
    List<OrderEvent> findByOrderIdOrderByIdAsc(Long orderId);
}
