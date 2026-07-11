package com.restaurant.reservations.dto

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.PositiveOrZero

data class TableRequest(
    @field:Min(1)
    val tableNumber: Int,

    val name: String? = null,

    @field:Min(1)
    val floor: Int,

    @field:Min(1)
    val capacity: Int,

    @field:PositiveOrZero
    val price: Double,

    val zoneId: Long? = null,

    @field:Min(1)
    val gridX: Int = 1,

    @field:Min(1)
    val gridY: Int = 1
)
