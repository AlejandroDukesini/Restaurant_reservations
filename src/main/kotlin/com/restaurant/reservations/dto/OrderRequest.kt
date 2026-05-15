package com.restaurant.reservations.dto

data class OrderRequest(
    val tableId: Long,
    val items: List<OrderItemRequest>,
    val notes: String? = null
)

data class OrderItemRequest(
    val itemName: String,
    val quantity: Int,
    val price: Double,
    val notes: String? = null
)
