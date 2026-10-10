package com.restaurant.reservations.mobile.data

import kotlinx.serialization.Serializable

// Espejo de los DTO del backend (com.restaurant.reservations.dto). Los nombres de los
// campos deben coincidir con el JSON de la API; los campos nuevos que agregue el
// servidor se ignoran (ignoreUnknownKeys en ApiClient).

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class LoginResponse(
    val token: String,
    val type: String = "Bearer",
    val userId: Long,
    val email: String,
    val role: String,
    val restaurantId: Long? = null
)

@Serializable
data class SessionUser(
    val userId: Long,
    val email: String,
    val role: String,
    val restaurantId: Long? = null
)

@Serializable
data class TableDto(
    val id: Long,
    val tableNumber: Int,
    val name: String? = null,
    val floor: Int,
    val capacity: Int,
    val price: Double,
    val restaurantId: Long,
    val active: Boolean,
    val zoneId: Long? = null,
    val zoneName: String? = null,
    val gridX: Int = 1,
    val gridY: Int = 1,
    val status: String = "AVAILABLE"
)

@Serializable
data class MenuItemDto(
    val id: Long,
    val name: String,
    val description: String? = null,
    val category: String,
    val price: Double,
    val protein: String? = null,
    val condiments: String? = null,
    val ingredients: String? = null,
    val preparationNotes: String? = null,
    val active: Boolean = true
)

@Serializable
data class OrderItemRequest(val menuItemId: Long, val quantity: Int, val notes: String? = null)

@Serializable
data class OrderRequest(val tableId: Long, val items: List<OrderItemRequest>, val notes: String? = null)

@Serializable
data class OrderItemDto(
    val id: Long,
    val menuItemId: Long? = null,
    val itemName: String,
    val quantity: Int,
    val price: Double,
    val status: String,
    val notes: String? = null
)

@Serializable
data class OrderDto(
    val id: Long,
    val tableId: Long,
    val tableNumber: Int,
    val employeeId: Long,
    val employeeName: String,
    val orderDate: String,
    val status: String,
    val totalAmount: Double,
    val items: List<OrderItemDto> = emptyList(),
    val notes: String? = null
)

@Serializable
data class CookQueueItemDto(
    val orderItemId: Long,
    val orderId: Long,
    val tableId: Long,
    val tableNumber: Int,
    val tableName: String? = null,
    val employeeName: String,
    val orderDate: String,
    val itemName: String,
    val quantity: Int,
    val status: String,
    val notes: String? = null,
    val category: String? = null,
    val protein: String? = null,
    val condiments: String? = null,
    val ingredients: String? = null,
    val preparationNotes: String? = null
)

@Serializable
data class ReservationDto(
    val id: Long,
    val tableId: Long,
    val customerId: Long,
    val customerName: String,
    val reservationDate: String,
    val numberOfGuests: Int,
    val status: String,
    val createdAt: String,
    val specialRequests: String? = null,
    val confirmed: Boolean
)

/** Formato de error comun de la API (GlobalExceptionHandler y RateLimitingFilter). */
@Serializable
data class ApiErrorBody(
    val status: Int? = null,
    val error: String? = null,
    val message: String? = null,
    val details: List<String> = emptyList()
)
