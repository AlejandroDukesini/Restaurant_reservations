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
    
    // Se usa en cada peticion con JWT. Exigir active=true revoca el acceso en cuanto
    // se da de baja a alguien; antes el token seguia valido hasta expirar (QA-SEC-02).
    @Transactional
    fun loadUserById(id: Long): UserDetails {
        val user = userRepository.findById(id)
            .filter { it.active }
            .orElseThrow { UsernameNotFoundException("User not found or inactive with id: $id") }
        
        return UserPrincipal(
            id = user.id!!,
            email = user.email,
            privatePassword = user.password,
            role = user.role,
            restaurantId = user.restaurant?.id
        )
    }
}
