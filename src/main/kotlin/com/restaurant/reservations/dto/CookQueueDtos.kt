package com.restaurant.reservations.dto

import java.time.LocalDateTime

// Un plato pendiente en la cola de cocina, con su receta fija para el cocinero.
data class CookQueueItem(
    val orderItemId: Long,
    val orderId: Long,
    val tableId: Long,
    val tableNumber: Int,
    val tableName: String?,
    val employeeName: String,
    val orderDate: LocalDateTime,
    val itemName: String,
    val quantity: Int,
    val status: String,
    val notes: String?,
    // Receta (desde el MenuItem)
    val category: String?,
    val protein: String?,
    val condiments: String?,
    val ingredients: String?,
    val preparationNotes: String?
)
