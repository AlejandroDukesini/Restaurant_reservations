package com.restaurant.reservations.controller

import com.restaurant.reservations.dto.RestaurantRegistrationRequest
import com.restaurant.reservations.dto.RestaurantResponse
import com.restaurant.reservations.service.RestaurantService
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/admin/restaurants")
@PreAuthorize("hasRole('ADMIN')")
class RestaurantController(
    private val restaurantService: RestaurantService
) {
    
    @PostMapping("/register")
    fun registerRestaurant(@RequestBody request: RestaurantRegistrationRequest): ResponseEntity<RestaurantResponse> {
        val response = restaurantService.registerRestaurant(request)
        return ResponseEntity.ok(response)
    }
    
    @GetMapping
    fun getAllRestaurants(): ResponseEntity<List<RestaurantResponse>> {
        val restaurants = restaurantService.getAllRestaurants()
        return ResponseEntity.ok(restaurants)
    }
    
    @GetMapping("/{id}")
    fun getRestaurantById(@PathVariable id: Long): ResponseEntity<RestaurantResponse> {
        val restaurant = restaurantService.getRestaurantById(id)
        return ResponseEntity.ok(restaurant)
    }
    
    @PutMapping("/{id}")
    fun updateRestaurant(
        @PathVariable id: Long,
        @RequestBody request: RestaurantRegistrationRequest
    ): ResponseEntity<RestaurantResponse> {
        val restaurant = restaurantService.updateRestaurant(id, request)
        return ResponseEntity.ok(restaurant)
    }
    
    @DeleteMapping("/{id}")
    fun deleteRestaurant(@PathVariable id: Long): ResponseEntity<Void> {
        restaurantService.deleteRestaurant(id)
        return ResponseEntity.noContent().build()
    }
}
