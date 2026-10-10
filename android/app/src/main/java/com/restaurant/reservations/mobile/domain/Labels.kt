package com.restaurant.reservations.mobile.domain

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

// Mismas etiquetas que site_web/src/utils/status.js.

val ORDER_STATUS_LABEL = mapOf(
    "PENDING" to "Pendiente",
    "IN_PROGRESS" to "En preparación",
    "COMPLETED" to "Completado",
    "CANCELLED" to "Cancelado"
)

val ITEM_STATUS_LABEL = mapOf(
    "PENDING" to "Pendiente",
    "PREPARING" to "Preparando",
    "READY" to "Listo"
)

val RESERVATION_STATUS_LABEL = mapOf(
    "PENDING" to "Pendiente",
    "CONFIRMED" to "Confirmada",
    "CANCELLED" to "Cancelada",
    "COMPLETED" to "Completada"
)

val CATEGORY_LABEL = mapOf(
    "STARTER" to "Entrada",
    "MAIN" to "Plato fuerte",
    "DESSERT" to "Postre",
    "DRINK" to "Bebida"
)

/** Igual que money() del SPA: "$76.50". */
fun money(value: Double): String = "$" + String.format(Locale.US, "%.2f", value)

private val DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

/** La API envia LocalDateTime ISO sin zona ("2030-05-01T20:00:00"). */
fun formatDateTime(iso: String): String = try {
    LocalDateTime.parse(iso).format(DATE_TIME)
} catch (e: DateTimeParseException) {
    iso
}

/** En cocina, "Preparando" solo tiene sentido para un plato pendiente; "Listo" siempre (la cola no trae listos). */
fun canMarkPreparing(status: String): Boolean = status == "PENDING"

/** Acciones de reserva que se ofrecen segun su estado (las mismas que el panel web). */
fun canConfirmReservation(status: String): Boolean = status == "PENDING"

fun canCancelReservation(status: String): Boolean = status == "PENDING" || status == "CONFIRMED"
