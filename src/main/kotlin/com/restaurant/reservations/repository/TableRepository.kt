package com.restaurant.reservations.repository

import com.restaurant.reservations.model.RestaurantTable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface TableRepository : JpaRepository<RestaurantTable, Long> {
    fun findByRestaurantId(restaurantId: Long): List<RestaurantTable>
    fun findByRestaurantIdAndFloor(restaurantId: Long, floor: Int): List<RestaurantTable>
    fun findByRestaurantIdAndActiveTrue(restaurantId: Long): List<RestaurantTable>
    fun findByRestaurantIdAndFloorAndActiveTrue(restaurantId: Long, floor: Int): List<RestaurantTable>
}
