package com.restaurant.reservations.dto

import jakarta.validation.constraints.FutureOrPresent
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.LocalDateTime

data class ReservationRequest(
    @field:NotNull
    val tableId: Long,

    @field:FutureOrPresent
    val reservationDate: LocalDateTime,

    @field:Min(1)
    @field:Max(100)
    val numberOfGuests: Int,

    @field:Size(max = 1000)
    val specialRequests: String? = null
)
