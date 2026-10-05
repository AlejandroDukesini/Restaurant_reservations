package com.restaurant.reservations.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
<<<<<<< HEAD
=======
import jakarta.validation.constraints.Size
>>>>>>> ed340704ed016e6dcc9e8c59c76220b3ee9c292e

import java.time.LocalDateTime

data class StaffRequest(
    @field:NotBlank
<<<<<<< HEAD
=======
    @field:Size(max = 120)
>>>>>>> ed340704ed016e6dcc9e8c59c76220b3ee9c292e
    val name: String,

    @field:Email
    @field:NotBlank
<<<<<<< HEAD
    val email: String,

    // Requerido al crear; opcional al actualizar (si viene, cambia la contraseña)
=======
    @field:Size(max = 254)
    val email: String,

    // Requerido al crear; opcional al actualizar (si viene, cambia la contraseña).
    // Minimo alineado con el registro publico: las cuentas de personal acceden a
    // datos de clientes y pedidos, no pueden tener una politica mas debil.
    @field:Size(min = 12, max = 128, message = "La contrasena debe tener entre 12 y 128 caracteres")
>>>>>>> ed340704ed016e6dcc9e8c59c76220b3ee9c292e
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
