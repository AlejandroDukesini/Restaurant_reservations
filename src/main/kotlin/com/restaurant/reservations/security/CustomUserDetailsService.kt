package com.restaurant.reservations.security

import com.restaurant.reservations.model.User
import com.restaurant.reservations.repository.UserRepository
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CustomUserDetailsService(
    private val userRepository: UserRepository
) : UserDetailsService {
    
    @Transactional
    override fun loadUserByUsername(email: String): UserDetails {
        val user = userRepository.findByEmailAndActiveTrue(email)
            .orElseThrow { UsernameNotFoundException("User not found with email: $email") }
        
        return UserPrincipal(
            id = user.id!!,
            email = user.email,
            privatePassword = user.password,
            role = user.role,
            restaurantId = user.restaurant?.id
        )
    }
    
    @Transactional
    fun loadUserById(id: Long): UserDetails {
        val user = userRepository.findById(id)
            .orElseThrow { UsernameNotFoundException("User not found with id: $id") }
        
        return UserPrincipal(
            id = user.id!!,
            email = user.email,
            privatePassword = user.password,
            role = user.role,
            restaurantId = user.restaurant?.id
        )
    }
}
