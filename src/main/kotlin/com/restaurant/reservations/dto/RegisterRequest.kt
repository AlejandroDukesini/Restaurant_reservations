package com.restaurant.reservations.dto

data class RegisterRequest(
    val email: String,
    val password: String,
    val name: String,
    val role: String,
    val restaurantId: Long? = null
)
