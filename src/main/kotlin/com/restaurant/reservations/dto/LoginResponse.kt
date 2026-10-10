package com.restaurant.reservations.dto

data class LoginResponse(
    val token: String,
    val type: String = "Bearer",
    val userId: Long,
    val email: String,
    val role: String,
    val restaurantId: Long?
)

/** Usuario de la sesion actual, con el rol vigente en base de datos. */
data class SessionUserResponse(
    val userId: Long,
    val email: String,
    val role: String,
    val restaurantId: Long?
)
