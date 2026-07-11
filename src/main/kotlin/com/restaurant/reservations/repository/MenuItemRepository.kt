package com.restaurant.reservations.repository

import com.restaurant.reservations.model.MenuItem
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface MenuItemRepository : JpaRepository<MenuItem, Long> {
    fun findByRestaurantIdAndActiveTrue(restaurantId: Long): List<MenuItem>
    fun findByIdAndRestaurantId(id: Long, restaurantId: Long): Optional<MenuItem>
}
