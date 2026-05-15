package com.restaurant.reservations.dto

data class TableResponse(
    val id: Long,
    val tableNumber: Int,
    val floor: Int,
    val capacity: Int,
    val price: Double,
    val restaurantId: Long,
    val active: Boolean
)
