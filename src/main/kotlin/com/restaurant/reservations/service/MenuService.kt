package com.restaurant.reservations.service

import com.restaurant.reservations.dto.MenuItemRequest
import com.restaurant.reservations.dto.MenuItemResponse
import com.restaurant.reservations.model.MenuCategory
import com.restaurant.reservations.model.MenuItem
import com.restaurant.reservations.repository.MenuItemRepository
import com.restaurant.reservations.repository.RestaurantRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class MenuService(
    private val menuItemRepository: MenuItemRepository,
    private val restaurantRepository: RestaurantRepository,
    private val authService: AuthService
) {

    fun getMenu(): List<MenuItemResponse> {
        val restaurantId = currentRestaurantId()
        return menuItemRepository.findByRestaurantIdAndActiveTrue(restaurantId)
            .map { toResponse(it) }
    }

    @Transactional
    fun createMenuItem(request: MenuItemRequest): MenuItemResponse {
        val restaurantId = currentRestaurantId()
        val restaurant = restaurantRepository.findById(restaurantId)
            .orElseThrow { IllegalArgumentException("Restaurant not found") }

        val item = MenuItem(
            name = request.name,
            description = request.description,
            category = parseCategory(request.category),
            price = request.price,
            protein = request.protein,
            condiments = request.condiments,
            ingredients = request.ingredients,
            preparationNotes = request.preparationNotes,
            restaurant = restaurant
        )
        return toResponse(menuItemRepository.save(item))
    }

    @Transactional
    fun updateMenuItem(id: Long, request: MenuItemRequest): MenuItemResponse {
        val restaurantId = currentRestaurantId()
        val item = menuItemRepository.findByIdAndRestaurantId(id, restaurantId)
            .orElseThrow { IllegalArgumentException("Menu item not found") }

        val updated = item.copy(
            name = request.name,
            description = request.description,
            category = parseCategory(request.category),
            price = request.price,
            protein = request.protein,
            condiments = request.condiments,
            ingredients = request.ingredients,
            preparationNotes = request.preparationNotes
        )
        return toResponse(menuItemRepository.save(updated))
    }

    @Transactional
    fun deleteMenuItem(id: Long) {
        val restaurantId = currentRestaurantId()
        val item = menuItemRepository.findByIdAndRestaurantId(id, restaurantId)
            .orElseThrow { IllegalArgumentException("Menu item not found") }
        menuItemRepository.save(item.copy(active = false))
    }

    private fun currentRestaurantId(): Long =
        authService.getCurrentUserRestaurantId()
            ?: throw IllegalArgumentException("User not associated with a restaurant")

    private fun parseCategory(value: String): MenuCategory =
        try {
            MenuCategory.valueOf(value.uppercase())
        } catch (ex: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid category: $value")
        }

    private fun toResponse(item: MenuItem): MenuItemResponse =
        MenuItemResponse(
            id = item.id!!,
            name = item.name,
            description = item.description,
            category = item.category.name,
            price = item.price,
            protein = item.protein,
            condiments = item.condiments,
            ingredients = item.ingredients,
            preparationNotes = item.preparationNotes,
            active = item.active
        )
}
