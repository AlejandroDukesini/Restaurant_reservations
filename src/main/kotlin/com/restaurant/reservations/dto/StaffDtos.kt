package com.restaurant.reservations.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank

import java.time.LocalDateTime

data class StaffRequest(
    @field:NotBlank
    val name: String,

    @field:Email
    @field:NotBlank
    val email: String,

    // Requerido al crear; opcional al actualizar (si viene, cambia la contraseña)
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
