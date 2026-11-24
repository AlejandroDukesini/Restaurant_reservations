package com.restaurant.reservations.repository;

import com.restaurant.reservations.model.MenuCategory;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MenuCategoryRepository extends JpaRepository<MenuCategory, Long> {
    List<MenuCategory> findByRestaurantIdAndActiveTrueOrderBySortOrderAscNameAsc(Long restaurantId);

    Optional<MenuCategory> findByIdAndRestaurantIdAndActiveTrue(Long id, Long restaurantId);

    Optional<MenuCategory> findFirstByRestaurantIdAndActiveTrueAndNameIgnoreCase(Long restaurantId, String name);
}
