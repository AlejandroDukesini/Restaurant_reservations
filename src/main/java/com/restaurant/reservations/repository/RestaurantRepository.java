package com.restaurant.reservations.repository;

import com.restaurant.reservations.model.Restaurant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RestaurantRepository extends JpaRepository<Restaurant, Long> {
    Optional<Restaurant> findBySlug(String slug);

    List<Restaurant> findByActiveTrue();

    Optional<Restaurant> findBySlugAndActiveTrue(String slug);
}
