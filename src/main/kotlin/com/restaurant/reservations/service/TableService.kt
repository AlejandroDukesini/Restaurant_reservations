package com.restaurant.reservations.service

import com.restaurant.reservations.dto.TableRequest
import com.restaurant.reservations.dto.TableResponse
import com.restaurant.reservations.model.RestaurantTable
import com.restaurant.reservations.repository.RestaurantRepository
import com.restaurant.reservations.repository.TableRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class TableService(
    private val tableRepository: TableRepository,
    private val restaurantRepository: RestaurantRepository,
    private val authService: AuthService
) {
    
    @Transactional
    fun createTable(request: TableRequest): TableResponse {
        val restaurantId = authService.getCurrentUserRestaurantId()
            ?: throw IllegalArgumentException("User not associated with a restaurant")
        
        val restaurant = restaurantRepository.findById(restaurantId)
            .orElseThrow { IllegalArgumentException("Restaurant not found") }
        
        val table = RestaurantTable(
            tableNumber = request.tableNumber,
            floor = request.floor,
            capacity = request.capacity,
            price = request.price,
            restaurant = restaurant
        )
        
        return toResponse(tableRepository.save(table))
    }
    
    fun getTablesByRestaurant(restaurantId: Long): List<TableResponse> {
        return tableRepository.findByRestaurantIdAndActiveTrue(restaurantId)
            .map { toResponse(it) }
    }
    
    fun getTablesByFloor(restaurantId: Long, floor: Int): List<TableResponse> {
        return tableRepository.findByRestaurantIdAndFloorAndActiveTrue(restaurantId, floor)
            .map { toResponse(it) }
    }
    
    fun getTableById(id: Long): TableResponse {
        val table = tableRepository.findById(id)
            .orElseThrow { IllegalArgumentException("Table not found") }
        return toResponse(table)
    }
    
    @Transactional
    fun updateTable(id: Long, request: TableRequest): TableResponse {
        val table = tableRepository.findById(id)
            .orElseThrow { IllegalArgumentException("Table not found") }
        
        val restaurantId = authService.getCurrentUserRestaurantId()
        if (table.restaurant.id != restaurantId) {
            throw IllegalArgumentException("Unauthorized access to table")
        }
        
        val updatedTable = table.copy(
            tableNumber = request.tableNumber,
            floor = request.floor,
            capacity = request.capacity,
            price = request.price
        )
        
        return toResponse(tableRepository.save(updatedTable))
    }
    
    @Transactional
    fun deleteTable(id: Long) {
        val table = tableRepository.findById(id)
            .orElseThrow { IllegalArgumentException("Table not found") }
        
        val restaurantId = authService.getCurrentUserRestaurantId()
        if (table.restaurant.id != restaurantId) {
            throw IllegalArgumentException("Unauthorized access to table")
        }
        
        val deactivatedTable = table.copy(active = false)
        tableRepository.save(deactivatedTable)
    }
    
    private fun toResponse(table: RestaurantTable): TableResponse {
        return TableResponse(
            id = table.id!!,
            tableNumber = table.tableNumber,
            floor = table.floor,
            capacity = table.capacity,
            price = table.price,
            restaurantId = table.restaurant.id!!,
            active = table.active
        )
    }
}
