package com.restaurant.reservations.repository;

import com.restaurant.reservations.model.RestaurantTable;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TableRepository extends JpaRepository<RestaurantTable, Long> {
    List<RestaurantTable> findByRestaurantId(Long restaurantId);

    List<RestaurantTable> findByRestaurantIdAndFloor(Long restaurantId, int floor);

    List<RestaurantTable> findByRestaurantIdAndActiveTrue(Long restaurantId);

    List<RestaurantTable> findByRestaurantIdAndFloorAndActiveTrue(Long restaurantId, int floor);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from RestaurantTable t where t.id = :tableId")
    Optional<RestaurantTable> findByIdForUpdate(@Param("tableId") Long tableId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from RestaurantTable t where t.id = :tableId and t.restaurant.id = :restaurantId")
    Optional<RestaurantTable> findByIdAndRestaurantIdForUpdate(
        @Param("tableId") Long tableId,
        @Param("restaurantId") Long restaurantId
    );
}
