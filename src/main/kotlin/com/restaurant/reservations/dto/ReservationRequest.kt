package com.restaurant.reservations.dto

import jakarta.validation.constraints.FutureOrPresent
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import java.time.LocalDateTime

data class ReservationRequest(
    @field:NotNull
    val tableId: Long,

    @field:FutureOrPresent
    val reservationDate: LocalDateTime,

    @field:Min(1)
    val numberOfGuests: Int,

    val specialRequests: String? = null
)
