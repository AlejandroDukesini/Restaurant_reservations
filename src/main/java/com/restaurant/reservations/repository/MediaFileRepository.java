package com.restaurant.reservations.repository;

import com.restaurant.reservations.model.MediaFile;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MediaFileRepository extends JpaRepository<MediaFile, String> {
    Optional<MediaFile> findByIdAndRestaurantId(String id, Long restaurantId);
}
