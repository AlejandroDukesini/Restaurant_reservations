package com.restaurant.reservations.controller

import com.restaurant.reservations.dto.RestaurantResponse
import com.restaurant.reservations.dto.TableResponse
import com.restaurant.reservations.service.RestaurantService
import com.restaurant.reservations.service.TableService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/public")
class PublicController(
    private val restaurantService: RestaurantService,
    private val tableService: TableService
) {
    
    @GetMapping("/restaurants")
    fun getAllRestaurants(): ResponseEntity<List<RestaurantResponse>> {
        val restaurants = restaurantService.getAllRestaurants()
        return ResponseEntity.ok(restaurants)
    }
    
    @GetMapping("/restaurants/{slug}")
    fun getRestaurantBySlug(@PathVariable slug: String): ResponseEntity<RestaurantResponse> {
        val restaurant = restaurantService.getRestaurantBySlug(slug)
        return ResponseEntity.ok(restaurant)
    }
    
    @GetMapping("/restaurants/{slug}/tables")
    fun getRestaurantTables(
        @PathVariable slug: String,
        @RequestParam(required = false) floor: Int?
    ): ResponseEntity<List<TableResponse>> {
        val restaurant = restaurantService.getRestaurantBySlug(slug)
        val tables = if (floor != null) {
            tableService.getTablesByFloor(restaurant.id, floor)
        } else {
            tableService.getTablesByRestaurant(restaurant.id)
        }
        return ResponseEntity.ok(tables)
    }
}
