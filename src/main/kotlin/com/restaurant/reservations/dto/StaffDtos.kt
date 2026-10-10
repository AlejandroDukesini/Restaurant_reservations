package com.restaurant.reservations.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

import java.time.LocalDateTime

data class StaffRequest(
    @field:NotBlank
    @field:Size(max = 120)
    val name: String,

    @field:Email
    @field:NotBlank
    @field:Size(max = 254)
    val email: String,

    // Requerido al crear; opcional al actualizar (si viene, cambia la contraseña).
    // Minimo alineado con el registro publico: las cuentas de personal acceden a
    // datos de clientes y pedidos, no pueden tener una politica mas debil.
    @field:Size(min = 12, max = 128, message = "La contrasena debe tener entre 12 y 128 caracteres")
    val password: String? = null,

    // EMPLOYEE | COOK
    val role: String,

    val active: Boolean = true
)

data class StaffResponse(
    val id: Long,
    val name: String,
    val email: String,
    val role: String,
    val active: Boolean,
    val createdAt: LocalDateTime
)
