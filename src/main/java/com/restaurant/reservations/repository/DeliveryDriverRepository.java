package com.restaurant.reservations.repository;

import com.restaurant.reservations.model.DeliveryDriver;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliveryDriverRepository extends JpaRepository<DeliveryDriver, Long> {
    List<DeliveryDriver> findByRestaurantIdOrderByNameAsc(Long restaurantId);

    Optional<DeliveryDriver> findByIdAndRestaurantId(Long id, Long restaurantId);

    Optional<DeliveryDriver> findByUserId(Long userId);
}
