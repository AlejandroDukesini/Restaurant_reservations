package com.restaurant.reservations.dto

data class TableRequest(
    val tableNumber: Int,
    val floor: Int,
    val capacity: Int,
    val price: Double
)
