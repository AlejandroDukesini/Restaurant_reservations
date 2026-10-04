package com.restaurant.reservations.service

import com.restaurant.reservations.dto.LoginRequest
import com.restaurant.reservations.dto.LoginResponse
import com.restaurant.reservations.dto.RegisterRequest
import com.restaurant.reservations.dto.RegisterResponse
import com.restaurant.reservations.model.Role
import com.restaurant.reservations.model.User
import com.restaurant.reservations.repository.RestaurantRepository
import com.restaurant.reservations.repository.UserRepository
import com.restaurant.reservations.security.JwtTokenProvider
import com.restaurant.reservations.security.UserPrincipal
import org.slf4j.LoggerFactory
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.AuthenticationException
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

    private val log = LoggerFactory.getLogger(AuthService::class.java)


    /**
     * Registro publico de clientes.
     *
     * El rol se fija en el servidor a CUSTOMER: aceptarlo del cuerpo de la peticion
     * permitia a cualquier anonimo crear una cuenta ADMIN (escalada de privilegios).
     * Tampoco se acepta restaurantId: un cliente no queda asociado a la organizacion
     * de ningun restaurante, y esa asociacion era lo que daba alcance de tenant.
     */
    @Transactional
    fun register(request: RegisterRequest): RegisterResponse {
        val email = request.email.trim().lowercase()

        if (userRepository.findByEmail(email).isPresent) {
            log.info("Registro rechazado: email ya existente")
            throw IllegalArgumentException("Email already registered")
        }

        val user = User(
            email = email,
            password = passwordEncoder.encode(request.password),
            name = request.name.trim(),
            role = Role.CUSTOMER,
            restaurant = null
        )

        val saved = userRepository.save(user)
        log.info("Alta de cliente id={} role={}", saved.id, saved.role)

        // Se devuelve un DTO y no la entidad User: la entidad serializaba el hash
        // BCrypt de la contrasena en la respuesta HTTP.
        return RegisterResponse(
            id = saved.id!!,
            email = saved.email,
            name = saved.name,
            role = saved.role.name
        )
    }

    fun login(request: LoginRequest): LoginResponse {
        val authentication = try {
            authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken(request.email.trim().lowercase(), request.password)
            )
        } catch (ex: AuthenticationException) {
            // Evidencia para deteccion de fuerza bruta / password spraying.
            // No se registra la contrasena ni se distingue "usuario inexistente"
            // de "clave incorrecta" (evita enumeracion de cuentas).
            log.warn("Login fallido para '{}'", request.email.trim().lowercase())
            throw ex
        }

        SecurityContextHolder.getContext().authentication = authentication

        val userPrincipal = authentication.principal as UserPrincipal
        val token = tokenProvider.generateToken(authentication)
        log.info("Login correcto userId={} role={}", userPrincipal.id, userPrincipal.role)

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
