package com.restaurant.reservations.service

import com.restaurant.reservations.dto.StaffRequest
import com.restaurant.reservations.dto.StaffResponse
import com.restaurant.reservations.model.Role
import com.restaurant.reservations.model.User
import com.restaurant.reservations.repository.RestaurantRepository
import com.restaurant.reservations.repository.UserRepository
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class StaffService(
    private val userRepository: UserRepository,
    private val restaurantRepository: RestaurantRepository,
    private val passwordEncoder: PasswordEncoder,
    private val authService: AuthService
) {

    private val staffRoles = listOf(Role.EMPLOYEE, Role.COOK)

    fun getStaff(): List<StaffResponse> {
        val restaurantId = currentRestaurantId()
        return userRepository.findByRestaurantIdAndRoleIn(restaurantId, staffRoles)
            .sortedBy { it.name }
            .map { toResponse(it) }
    }

    @Transactional
    fun createStaff(request: StaffRequest): StaffResponse {
        val restaurantId = currentRestaurantId()
        val restaurant = restaurantRepository.findById(restaurantId)
            .orElseThrow { IllegalArgumentException("Restaurant not found") }

        if (userRepository.findByEmail(request.email).isPresent) {
            throw IllegalArgumentException("Email already registered")
        }

        val password = request.password?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("Password is required")

        val user = User(
            email = request.email,
            password = passwordEncoder.encode(password),
            name = request.name,
            role = parseStaffRole(request.role),
            restaurant = restaurant,
            active = request.active
        )
        return toResponse(userRepository.save(user))
    }

    @Transactional
    fun updateStaff(id: Long, request: StaffRequest): StaffResponse {
        val restaurantId = currentRestaurantId()
        val user = userRepository.findByIdAndRestaurantId(id, restaurantId)
            .orElseThrow { IllegalArgumentException("Staff member not found") }

        if (user.role !in staffRoles) {
            throw IllegalArgumentException("User is not a staff member")
        }

        val newPassword = request.password?.takeIf { it.isNotBlank() }

        val updated = user.copy(
            name = request.name,
            email = request.email,
            role = parseStaffRole(request.role),
            active = request.active,
            password = newPassword?.let { passwordEncoder.encode(it) } ?: user.password
        )
        return toResponse(userRepository.save(updated))
    }

    @Transactional
    fun deleteStaff(id: Long) {
        val restaurantId = currentRestaurantId()
        val user = userRepository.findByIdAndRestaurantId(id, restaurantId)
            .orElseThrow { IllegalArgumentException("Staff member not found") }
        userRepository.save(user.copy(active = false))
    }

    private fun currentRestaurantId(): Long =
        authService.getCurrentUserRestaurantId()
            ?: throw IllegalArgumentException("User not associated with a restaurant")

    private fun parseStaffRole(value: String): Role {
        val role = try {
            Role.valueOf(value.uppercase())
        } catch (ex: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid role: $value")
        }
        if (role !in staffRoles) {
            throw IllegalArgumentException("Role must be EMPLOYEE or COOK")
        }
        return role
    }

    private fun toResponse(user: User): StaffResponse =
        StaffResponse(
            id = user.id!!,
            name = user.name,
            email = user.email,
            role = user.role.name,
            active = user.active,
            createdAt = user.createdAt
        )
}
