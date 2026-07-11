package com.restaurant.reservations.repository

import com.restaurant.reservations.model.Role
import com.restaurant.reservations.model.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface UserRepository : JpaRepository<User, Long> {
    fun findByEmail(email: String): Optional<User>
    fun findByRestaurantId(restaurantId: Long): List<User>
    fun findByEmailAndActiveTrue(email: String): Optional<User>
    fun findByRestaurantIdAndRoleIn(restaurantId: Long, roles: List<Role>): List<User>
    fun findByIdAndRestaurantId(id: Long, restaurantId: Long): Optional<User>
}
