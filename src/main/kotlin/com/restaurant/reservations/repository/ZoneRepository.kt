package com.restaurant.reservations.repository

import com.restaurant.reservations.model.Zone
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface ZoneRepository : JpaRepository<Zone, Long> {
    fun findByRestaurantIdAndActiveTrueOrderBySortOrderAsc(restaurantId: Long): List<Zone>
    fun findByIdAndRestaurantId(id: Long, restaurantId: Long): Optional<Zone>
}
