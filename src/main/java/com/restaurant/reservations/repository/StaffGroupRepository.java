package com.restaurant.reservations.repository;

import com.restaurant.reservations.model.StaffGroup;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StaffGroupRepository extends JpaRepository<StaffGroup, Long> {
    List<StaffGroup> findByRestaurantIdAndActiveTrueOrderByNameAsc(Long restaurantId);

    Optional<StaffGroup> findByIdAndRestaurantIdAndActiveTrue(Long id, Long restaurantId);

    @Query("SELECT g FROM StaffGroup g JOIN g.members m WHERE m.id = :userId AND g.active = true ORDER BY g.id ASC")
    List<StaffGroup> findActiveByMember(@Param("userId") Long userId);

    @Query("SELECT COUNT(g) > 0 FROM StaffGroup g WHERE g.restaurantId = :restaurantId AND g.active = true "
        + "AND LOWER(g.name) = LOWER(:name) AND (:excludeId IS NULL OR g.id <> :excludeId)")
    boolean existsActiveName(@Param("restaurantId") Long restaurantId, @Param("name") String name,
                             @Param("excludeId") Long excludeId);
}
