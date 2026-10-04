package com.restaurant.reservations.dto

import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

data class OrderRequest(
    @field:NotNull
    val tableId: Long,

    // @Valid en la lista propaga la validacion a cada elemento; sin el, los
    // limites de OrderItemRequest no se aplicaban.
    @field:Valid
    @field:NotEmpty
    @field:Size(max = 100)
    val items: List<OrderItemRequest>,

    @field:Size(max = 1000)
    val notes: String? = null
)

data class OrderItemRequest(
    @field:NotNull
    val menuItemId: Long,

    // Techo por plato: sin el, una cantidad enorme desvirtua el total del pedido
    // y la cola de cocina.
    @field:Min(1)
    @field:Max(500)
    val quantity: Int,

    @field:Size(max = 500)
    val notes: String? = null
)
