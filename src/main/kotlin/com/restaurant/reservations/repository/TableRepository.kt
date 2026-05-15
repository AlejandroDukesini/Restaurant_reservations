package com.restaurant.reservations.repository

import com.restaurant.reservations.model.Table
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface TableRepository : JpaRepository<Table, Long> {
    fun findByRestaurantId(restaurantId: Long): List<Table>
    fun findByRestaurantIdAndFloor(restaurantId: Long, floor: Int): List<Table>
    fun findByRestaurantIdAndActiveTrue(restaurantId: Long): List<Table>
    fun findByRestaurantIdAndFloorAndActiveTrue(restaurantId: Long, floor: Int): List<Table>
}
