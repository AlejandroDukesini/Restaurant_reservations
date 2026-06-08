package com.restaurant.reservations.dto

data class TableResponse(
    val id: Long,
    val tableNumber: Int,
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
