package com.restaurant.reservations.dto

import java.time.LocalDateTime

data class ReservationRequest(
    val tableId: Long,
    val reservationDate: LocalDateTime,
    val numberOfGuests: Int,
    val specialRequests: String? = null
)
