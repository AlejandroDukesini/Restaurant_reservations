package com.restaurant.reservations.service

import com.restaurant.reservations.dto.RestaurantRegistrationRequest
import com.restaurant.reservations.dto.RestaurantResponse
import com.restaurant.reservations.exception.ResourceNotFoundException
import com.restaurant.reservations.exception.ValidationBusinessException
import com.restaurant.reservations.model.Restaurant
import com.restaurant.reservations.model.Role
import com.restaurant.reservations.model.User
import com.restaurant.reservations.repository.RestaurantRepository
import com.restaurant.reservations.repository.UserRepository
import com.restaurant.reservations.service.WebsiteGeneratorService
import org.slf4j.LoggerFactory
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class RestaurantService(
    private val restaurantRepository: RestaurantRepository,
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val websiteGeneratorService: WebsiteGeneratorService,
    private val authService: AuthService
) {

    private val log = LoggerFactory.getLogger(RestaurantService::class.java)


    @Transactional
    fun registerRestaurant(request: RestaurantRegistrationRequest): RestaurantResponse {
        if (restaurantRepository.findBySlug(generateSlug(request.name)).isPresent) {
            throw IllegalArgumentException("Restaurant name already exists")
        }
        
        val slug = generateSlug(request.name)

        // Mismo criterio que el login (minusculas, sin espacios) y unicidad comprobada
        // antes de escribir nada: el duplicado terminaba en un 500 por la restriccion unique.
        val adminEmail = request.adminEmail.trim().lowercase()
        if (userRepository.findByEmail(adminEmail).isPresent) {
            throw IllegalArgumentException("Email already registered")
        }

        val restaurant = Restaurant(
            name = request.name,
            slug = slug,
            description = request.description,
            address = request.address,
            phone = request.phone,
            email = request.email,
            numberOfTables = request.numberOfTables,
            numberOfChairs = request.numberOfChairs,
            numberOfFloors = request.numberOfFloors
        )

        // websiteUrl es NOT NULL en la tabla: guardar primero con null y actualizar
        // despues hacia fallar todo registro con una violacion de integridad (500).
        // El generador solo necesita slug y datos descriptivos, no el id.
        val websiteUrl = websiteGeneratorService.generateWebsite(restaurant)
        val savedRestaurant = restaurantRepository.save(restaurant.copy(websiteUrl = websiteUrl))

        val admin = User(
            email = adminEmail,
            password = passwordEncoder.encode(request.adminPassword),
            name = request.adminName,
            role = Role.ADMIN,
            restaurant = savedRestaurant
        )

        userRepository.save(admin)

        return toResponse(savedRestaurant)
    }
    
    fun getAllRestaurants(): List<RestaurantResponse> {
        return restaurantRepository.findByActiveTrue().map { toResponse(it) }
    }
    
    fun getRestaurantBySlug(slug: String): RestaurantResponse {
        val restaurant = restaurantRepository.findBySlugAndActiveTrue(slug)
            .orElseThrow { IllegalArgumentException("Restaurant not found") }
        return toResponse(restaurant)
    }
    
    fun getRestaurantById(id: Long): RestaurantResponse {
        val restaurant = restaurantRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Restaurant not found") }
        requireOwnRestaurant(id)
        return toResponse(restaurant)
    }

    @Transactional
    fun updateRestaurant(id: Long, request: RestaurantRegistrationRequest): RestaurantResponse {
        val restaurant = restaurantRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Restaurant not found") }

        // ADMIN es el administrador de UN restaurante, no un superusuario global:
        // sin este control cualquier admin podia editar o dar de baja el
        // restaurante de otro tenant pasando su id.
        requireOwnRestaurant(id)

        val updatedRestaurant = restaurant.copy(
            name = request.name,
            description = request.description,
            address = request.address,
            phone = request.phone,
            email = request.email,
            numberOfTables = request.numberOfTables,
            numberOfChairs = request.numberOfChairs,
            numberOfFloors = request.numberOfFloors
        )
        
        return toResponse(restaurantRepository.save(updatedRestaurant))
    }
    
    @Transactional
    fun deleteRestaurant(id: Long) {
        val restaurant = restaurantRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Restaurant not found") }

        requireOwnRestaurant(id)

        log.warn("Restaurante {} desactivado por userId={}", id, authService.getCurrentUserId())
        val deactivatedRestaurant = restaurant.copy(active = false)
        restaurantRepository.save(deactivatedRestaurant)
    }

    private fun requireOwnRestaurant(id: Long) {
        val callerRestaurantId = authService.getCurrentUserRestaurantId()
        if (callerRestaurantId != id) {
            log.warn(
                "Acceso cross-tenant a restaurante denegado: userId={} (restaurante {}) pidio restaurante {}",
                authService.getCurrentUserId(), callerRestaurantId, id
            )
            throw ResourceNotFoundException("Restaurant not found")
        }
    }

    private fun generateSlug(name: String): String {
        val slug = name.lowercase()
            .replace(" ", "-")
            .replace("[^a-z0-9-]".toRegex(), "")
            .trim('-')
        // Un slug vacio (nombre solo con simbolos) haria que el generador de sitios
        // escribiera en el directorio raiz de salida en vez de en un subdirectorio.
        if (slug.isEmpty()) {
            throw ValidationBusinessException("Restaurant name must contain letters or numbers")
        }
        return slug
    }


    private fun toResponse(restaurant: Restaurant): RestaurantResponse {
        return RestaurantResponse(
            id = restaurant.id!!,
            name = restaurant.name,
            slug = restaurant.slug,
            description = restaurant.description,
            address = restaurant.address,
            phone = restaurant.phone,
            email = restaurant.email,
            numberOfTables = restaurant.numberOfTables,
            numberOfChairs = restaurant.numberOfChairs,
            numberOfFloors = restaurant.numberOfFloors,
            websiteUrl = restaurant.websiteUrl,
            createdAt = restaurant.createdAt.toString(),
            active = restaurant.active
        )
    }
}
