package com.restaurant.reservations.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/**
 * Alta de cliente. El rol NO se acepta desde el cliente: el registro publico
 * siempre crea un CUSTOMER. El personal (EMPLOYEE/COOK) se crea desde
 * /api/admin/staff y los administradores desde /api/admin/restaurants/register.
 */
data class RegisterRequest(
    @field:NotBlank
    @field:Email
    @field:Size(max = 254)
    val email: String,

    @field:NotBlank
    @field:Size(min = 12, max = 128, message = "La contrasena debe tener entre 12 y 128 caracteres")
    val password: String,

    @field:NotBlank
    @field:Size(max = 120)
    val name: String
)

/** Respuesta del registro: nunca incluye el hash de la contrasena. */
data class RegisterResponse(
    val id: Long,
    val email: String,
    val name: String,
    val role: String
)
