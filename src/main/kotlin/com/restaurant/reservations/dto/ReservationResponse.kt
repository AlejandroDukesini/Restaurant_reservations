package com.restaurant.reservations.dto

import java.time.LocalDateTime

data class ReservationResponse(
    val id: Long,
    val tableId: Long,
    val customerId: Long,
    val customerName: String,
    val reservationDate: LocalDateTime,
    val numberOfGuests: Int,
    val status: String,
    val createdAt: LocalDateTime,
    val specialRequests: String?,
    val confirmed: Boolean
)
