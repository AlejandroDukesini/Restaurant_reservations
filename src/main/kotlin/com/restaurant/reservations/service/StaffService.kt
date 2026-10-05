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

<<<<<<< HEAD
        if (userRepository.findByEmail(request.email).isPresent) {
=======
        val email = normalizeEmail(request.email)
        if (userRepository.findByEmail(email).isPresent) {
>>>>>>> ed340704ed016e6dcc9e8c59c76220b3ee9c292e
            throw IllegalArgumentException("Email already registered")
        }

        val password = request.password?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("Password is required")

        val user = User(
<<<<<<< HEAD
            email = request.email,
=======
            email = email,
>>>>>>> ed340704ed016e6dcc9e8c59c76220b3ee9c292e
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

<<<<<<< HEAD
=======
        val email = normalizeEmail(request.email)
        if (email != user.email && userRepository.findByEmail(email).isPresent) {
            throw IllegalArgumentException("Email already registered")
        }

>>>>>>> ed340704ed016e6dcc9e8c59c76220b3ee9c292e
        val newPassword = request.password?.takeIf { it.isNotBlank() }

        val updated = user.copy(
            name = request.name,
<<<<<<< HEAD
            email = request.email,
=======
            email = email,
>>>>>>> ed340704ed016e6dcc9e8c59c76220b3ee9c292e
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

<<<<<<< HEAD
=======
    // El login busca el email en minusculas y sin espacios (AuthService.login): si aqui
    // se guardaba tal cual, una cuenta creada con mayusculas no podia iniciar sesion.
    private fun normalizeEmail(value: String): String = value.trim().lowercase()

>>>>>>> ed340704ed016e6dcc9e8c59c76220b3ee9c292e
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
