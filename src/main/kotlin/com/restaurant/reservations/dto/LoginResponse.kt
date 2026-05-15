package com.restaurant.reservations.dto

data class LoginResponse(
    val token: String,
    val type: String = "Bearer",
    val userId: Long,
    val email: String,
    val role: String,
    val restaurantId: Long?
)
