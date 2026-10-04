package com.restaurant.reservations.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class LoginRequest(
    @field:NotBlank
    @field:Size(max = 254)
    val email: String,

    // Techo de longitud: BCrypt solo usa los primeros 72 bytes y una entrada
    // ilimitada permite gastar CPU del servidor a voluntad (DoS por hashing).
    @field:NotBlank
    @field:Size(max = 128)
    val password: String
)
