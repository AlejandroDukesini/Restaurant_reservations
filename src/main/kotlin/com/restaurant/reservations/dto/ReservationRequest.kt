package com.restaurant.reservations.dto

import jakarta.validation.constraints.FutureOrPresent
<<<<<<< HEAD
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
=======
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
>>>>>>> ed340704ed016e6dcc9e8c59c76220b3ee9c292e
import java.time.LocalDateTime

data class ReservationRequest(
    @field:NotNull
    val tableId: Long,

    @field:FutureOrPresent
    val reservationDate: LocalDateTime,

    @field:Min(1)
<<<<<<< HEAD
    val numberOfGuests: Int,

=======
    @field:Max(100)
    val numberOfGuests: Int,

    @field:Size(max = 1000)
>>>>>>> ed340704ed016e6dcc9e8c59c76220b3ee9c292e
    val specialRequests: String? = null
)
