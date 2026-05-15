package com.restaurant.reservations.service

import com.restaurant.reservations.dto.LoginRequest
import com.restaurant.reservations.dto.LoginResponse
import com.restaurant.reservations.dto.RegisterRequest
import com.restaurant.reservations.model.Role
import com.restaurant.reservations.model.User
import com.restaurant.reservations.repository.RestaurantRepository
import com.restaurant.reservations.repository.UserRepository
import com.restaurant.reservations.security.JwtTokenProvider
import com.restaurant.reservations.security.UserPrincipal
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AuthService(
    private val authenticationManager: AuthenticationManager,
    private val userRepository: UserRepository,
    private val restaurantRepository: RestaurantRepository,
    private val passwordEncoder: PasswordEncoder,
    private val tokenProvider: JwtTokenProvider
) {
    
    @Transactional
    fun register(request: RegisterRequest): User {
        if (userRepository.findByEmail(request.email).isPresent) {
            throw IllegalArgumentException("Email already registered")
        }
        
        val restaurant = if (request.restaurantId != null) {
            restaurantRepository.findById(request.restaurantId)
                .orElseThrow { IllegalArgumentException("Restaurant not found") }
        } else null
        
        val user = User(
            email = request.email,
            password = passwordEncoder.encode(request.password),
            name = request.name,
            role = Role.valueOf(request.role.uppercase()),
            restaurant = restaurant
        )
        
        return userRepository.save(user)
    }
    
    fun login(request: LoginRequest): LoginResponse {
        val authentication = authenticationManager.authenticate(
            UsernamePasswordAuthenticationToken(request.email, request.password)
        )
        
        SecurityContextHolder.getContext().authentication = authentication
        
        val userPrincipal = authentication.principal as UserPrincipal
        val token = tokenProvider.generateToken(authentication)
        
        return LoginResponse(
            token = token,
            userId = userPrincipal.id,
            email = userPrincipal.email,
            role = userPrincipal.role.name,
            restaurantId = userPrincipal.restaurantId
        )
    }
    
    fun getCurrentUser(): User {
        val authentication = SecurityContextHolder.getContext().authentication
        val userPrincipal = authentication.principal as UserPrincipal
        return userRepository.findById(userPrincipal.id)
            .orElseThrow { IllegalArgumentException("User not found") }
    }
    
    fun getCurrentUserId(): Long {
        val authentication = SecurityContextHolder.getContext().authentication
        return (authentication.principal as UserPrincipal).id
    }
    
    fun getCurrentUserRole(): Role {
        val authentication = SecurityContextHolder.getContext().authentication
        return (authentication.principal as UserPrincipal).role
    }
    
    fun getCurrentUserRestaurantId(): Long? {
        val authentication = SecurityContextHolder.getContext().authentication
        return (authentication.principal as UserPrincipal).restaurantId
    }
}
