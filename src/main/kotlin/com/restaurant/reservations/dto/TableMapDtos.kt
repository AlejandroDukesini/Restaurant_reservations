package com.restaurant.reservations.dto

import jakarta.validation.constraints.FutureOrPresent
import jakarta.validation.constraints.Email
<<<<<<< HEAD
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
=======
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
>>>>>>> ed340704ed016e6dcc9e8c59c76220b3ee9c292e
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

data class TableMapResponse(
    val restaurantId: Long,
    val date: LocalDate,
    val time: LocalTime,
    val zones: List<TableMapZoneResponse>
)

data class TableMapZoneResponse(
    val id: Long?,
    val name: String,
    val code: String,
    val tables: List<TableMapTableResponse>
)

data class TableMapTableResponse(
    val id: Long,
    val tableNumber: Int,
    val capacity: Int,
    val gridX: Int,
    val gridY: Int,
    val zoneName: String,
    val availability: String
)

data class PublicReservationRequest(
    @field:NotNull
    val restaurantId: Long,

    @field:NotNull
    val tableId: Long,

    @field:FutureOrPresent
    val reservationDate: LocalDateTime,

<<<<<<< HEAD
    @field:Min(1)
    val numberOfGuests: Int,

    @field:NotBlank
=======
    // Este endpoint es anonimo: los limites superiores evitan que se use para
    // llenar la base de datos o desvirtuar el aforo.
    @field:Min(1)
    @field:Max(100)
    val numberOfGuests: Int,

    @field:NotBlank
    @field:Size(max = 120)
>>>>>>> ed340704ed016e6dcc9e8c59c76220b3ee9c292e
    val customerName: String,

    @field:NotBlank
    @field:Email
<<<<<<< HEAD
    val customerEmail: String,

=======
    @field:Size(max = 254)
    val customerEmail: String,

    @field:Size(max = 1000)
>>>>>>> ed340704ed016e6dcc9e8c59c76220b3ee9c292e
    val specialRequests: String? = null
)
