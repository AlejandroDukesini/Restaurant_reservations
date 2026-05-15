package com.restaurant.reservations.repository

import com.restaurant.reservations.model.Restaurant
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface RestaurantRepository : JpaRepository<Restaurant, Long> {
    fun findBySlug(slug: String): Optional<Restaurant>
    fun findByActiveTrue(): List<Restaurant>
    fun findBySlugAndActiveTrue(slug: String): Optional<Restaurant>
}
