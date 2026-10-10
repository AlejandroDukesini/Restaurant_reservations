package com.restaurant.reservations.repository;

import com.restaurant.reservations.model.Zone;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ZoneRepository extends JpaRepository<Zone, Long> {
    List<Zone> findByRestaurantIdAndActiveTrueOrderBySortOrderAsc(Long restaurantId);

    Optional<Zone> findByIdAndRestaurantId(Long id, Long restaurantId);
}
