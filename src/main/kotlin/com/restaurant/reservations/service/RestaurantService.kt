package com.restaurant.reservations.service

import com.restaurant.reservations.dto.RestaurantRegistrationRequest
import com.restaurant.reservations.dto.RestaurantResponse
import com.restaurant.reservations.model.Restaurant
import com.restaurant.reservations.model.Role
import com.restaurant.reservations.model.User
import com.restaurant.reservations.repository.RestaurantRepository
import com.restaurant.reservations.repository.UserRepository
import com.restaurant.reservations.security.PasswordEncoder
import com.restaurant.reservations.service.WebsiteGeneratorService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class RestaurantService(
    private val restaurantRepository: RestaurantRepository,
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val websiteGeneratorService: WebsiteGeneratorService
) {
    
    @Transactional
    fun registerRestaurant(request: RestaurantRegistrationRequest): RestaurantResponse {
        if (restaurantRepository.findBySlug(generateSlug(request.name)).isPresent) {
            throw IllegalArgumentException("Restaurant name already exists")
        }
        
        val slug = generateSlug(request.name)
        
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
        
        val savedRestaurant = restaurantRepository.save(restaurant)
        
        val admin = User(
            email = request.adminEmail,
            password = passwordEncoder.encode(request.adminPassword),
            name = request.adminName,
            role = Role.ADMIN,
            restaurant = savedRestaurant
        )
        
        userRepository.save(admin)
        
        val websiteUrl = websiteGeneratorService.generateWebsite(savedRestaurant)
        savedRestaurant.websiteUrl = websiteUrl
        restaurantRepository.save(savedRestaurant)
        
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
            .orElseThrow { IllegalArgumentException("Restaurant not found") }
        return toResponse(restaurant)
    }
    
    @Transactional
    fun updateRestaurant(id: Long, request: RestaurantRegistrationRequest): RestaurantResponse {
        val restaurant = restaurantRepository.findById(id)
            .orElseThrow { IllegalArgumentException("Restaurant not found") }
        
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
            .orElseThrow { IllegalArgumentException("Restaurant not found") }
        
        val deactivatedRestaurant = restaurant.copy(active = false)
        restaurantRepository.save(deactivatedRestaurant)
    }
    
    private fun generateSlug(name: String): String {
        return name.lowercase()
            .replace(" ", "-")
            .replace("[^a-z0-9-]".toRegex(), "")
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
