package com.restaurant.reservations.repository;

import com.restaurant.reservations.model.DeliveryPlatform;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliveryPlatformRepository extends JpaRepository<DeliveryPlatform, Long> {
    List<DeliveryPlatform> findByRestaurantIdOrderByNameAsc(Long restaurantId);

    Optional<DeliveryPlatform> findByIdAndRestaurantId(Long id, Long restaurantId);
}
