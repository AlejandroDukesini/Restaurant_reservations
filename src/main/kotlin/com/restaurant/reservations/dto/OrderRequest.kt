package com.restaurant.reservations.dto

import jakarta.validation.constraints.Min

data class OrderRequest(
    val tableId: Long,
    val items: List<OrderItemRequest>,
    val notes: String? = null
)

data class OrderItemRequest(
    val menuItemId: Long,

    @field:Min(1)
    val quantity: Int,

    val notes: String? = null
)
