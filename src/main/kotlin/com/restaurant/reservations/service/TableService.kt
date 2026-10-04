package com.restaurant.reservations.service

import com.restaurant.reservations.dto.TableRequest
import com.restaurant.reservations.dto.TableResponse
import com.restaurant.reservations.exception.ResourceNotFoundException
import com.restaurant.reservations.exception.ValidationBusinessException
import com.restaurant.reservations.model.RestaurantTable
import com.restaurant.reservations.repository.RestaurantRepository
import com.restaurant.reservations.repository.TableRepository
import com.restaurant.reservations.repository.ZoneRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class TableService(
    private val tableRepository: TableRepository,
    private val restaurantRepository: RestaurantRepository,
    private val zoneRepository: ZoneRepository,
    private val authService: AuthService
) {

    private val log = LoggerFactory.getLogger(TableService::class.java)


    @Transactional
    fun createTable(request: TableRequest): TableResponse {
        val restaurantId = authService.getCurrentUserRestaurantId()
            ?: throw IllegalArgumentException("User not associated with a restaurant")
        
        val restaurant = restaurantRepository.findById(restaurantId)
            .orElseThrow { IllegalArgumentException("Restaurant not found") }

        val zone = request.zoneId?.let {
            zoneRepository.findByIdAndRestaurantId(it, restaurantId)
                .orElseThrow { IllegalArgumentException("Zone not found for restaurant") }
        }
        
        val table = RestaurantTable(
            tableNumber = request.tableNumber,
            name = request.name,
            floor = request.floor,
            capacity = request.capacity,
            price = request.price,
            zone = zone,
            gridX = request.gridX,
            gridY = request.gridY,
            restaurant = restaurant
        )
        
        return toResponse(tableRepository.save(table))
    }
    
    fun getTablesByRestaurant(restaurantId: Long): List<TableResponse> {
        return tableRepository.findByRestaurantIdAndActiveTrue(restaurantId)
            .map { toResponse(it) }
    }

    fun getMyTables(floor: Int?): List<TableResponse> {
        val restaurantId = authService.getCurrentUserRestaurantId()
            ?: throw IllegalArgumentException("User not associated with a restaurant")
        return if (floor != null) getTablesByFloor(restaurantId, floor)
        else getTablesByRestaurant(restaurantId)
    }
    
    fun getTablesByFloor(restaurantId: Long, floor: Int): List<TableResponse> {
        return tableRepository.findByRestaurantIdAndFloorAndActiveTrue(restaurantId, floor)
            .map { toResponse(it) }
    }
    
    fun getTableById(id: Long): TableResponse {
        val table = tableRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Table not found") }
        // Faltaba el control de tenant: cualquier mesero podia leer mesas
        // (numero, capacidad, precio) de otros restaurantes iterando el id.
        requireOwnRestaurant(table.restaurant.id)
        return toResponse(table)
    }

    @Transactional
    fun updateTable(id: Long, request: TableRequest): TableResponse {
        val table = tableRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Table not found") }

        val restaurantId = requireOwnRestaurant(table.restaurant.id)

        val zone = request.zoneId?.let {
            zoneRepository.findByIdAndRestaurantId(it, restaurantId)
                .orElseThrow { ResourceNotFoundException("Zone not found for restaurant") }
        }

        val updatedTable = table.copy(
            tableNumber = request.tableNumber,
            name = request.name,
            floor = request.floor,
            capacity = request.capacity,
            price = request.price,
            zone = zone,
            gridX = request.gridX,
            gridY = request.gridY
        )
        
        return toResponse(tableRepository.save(updatedTable))
    }
    
    @Transactional
    fun deleteTable(id: Long) {
        val table = tableRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Table not found") }

        requireOwnRestaurant(table.restaurant.id)

        val deactivatedTable = table.copy(active = false)
        tableRepository.save(deactivatedTable)
    }

    /**
     * Exige que el recurso pertenezca al restaurante del usuario autenticado y
     * devuelve ese id. Responde 404 para no confirmar la existencia de mesas ajenas.
     */
    private fun requireOwnRestaurant(ownerRestaurantId: Long?): Long {
        val callerRestaurantId = authService.getCurrentUserRestaurantId()
            ?: throw ValidationBusinessException("User not associated with a restaurant")
        if (ownerRestaurantId != callerRestaurantId) {
            log.warn(
                "Acceso cross-tenant a mesa denegado: userId={} (restaurante {}) pidio recurso del restaurante {}",
                authService.getCurrentUserId(), callerRestaurantId, ownerRestaurantId
            )
            throw ResourceNotFoundException("Table not found")
        }
        return callerRestaurantId
    }


    private fun toResponse(table: RestaurantTable): TableResponse {
        return TableResponse(
            id = table.id!!,
            tableNumber = table.tableNumber,
            name = table.name,
            floor = table.floor,
            capacity = table.capacity,
            price = table.price,
            restaurantId = table.restaurant.id!!,
            active = table.active,
            zoneId = table.zone?.id,
            zoneName = table.zone?.name,
            gridX = table.gridX,
            gridY = table.gridY,
            status = table.status.name
        )
    }
}
