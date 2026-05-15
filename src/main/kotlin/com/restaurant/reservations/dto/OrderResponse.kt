package com.restaurant.reservations.dto

import java.time.LocalDateTime

data class OrderResponse(
    val id: Long,
    val tableId: Long,
    val tableNumber: Int,
    val employeeId: Long,
    val employeeName: String,
    val orderDate: LocalDateTime,
    val status: String,
    val totalAmount: Double,
    val items: List<OrderItemResponse>,
    val notes: String?
)

data class OrderItemResponse(
    val id: Long,
    val itemName: String,
    val quantity: Int,
    val price: Double,
    val notes: String?
)
