package com.restaurant.reservations.mobile.domain

import com.restaurant.reservations.mobile.data.MenuItemDto
import com.restaurant.reservations.mobile.data.OrderItemRequest

/**
 * Borrador de pedido de una mesa: menuItemId -> cantidad. Inmutable para que Compose
 * detecte cada cambio. El total es solo orientativo: el servidor lo recalcula con sus precios.
 */
data class OrderDraft(val quantities: Map<Long, Int> = emptyMap()) {

    fun quantityOf(menuItemId: Long): Int = quantities[menuItemId] ?: 0

    /** Suma [delta] (+1 / -1); llegar a cero quita el plato del borrador. */
    fun change(menuItemId: Long, delta: Int): OrderDraft {
        val next = quantityOf(menuItemId) + delta
        return OrderDraft(if (next <= 0) quantities - menuItemId else quantities + (menuItemId to next))
    }

    fun isEmpty(): Boolean = quantities.isEmpty()

    fun total(menu: List<MenuItemDto>): Double {
        val prices = menu.associate { it.id to it.price }
        var total = 0.0
        for ((id, qty) in quantities) total += (prices[id] ?: 0.0) * qty
        return total
    }

    /** Lineas en el formato de OrderRequest; solo se envia id y cantidad (nombre y precio los pone el servidor). */
    fun toRequestItems(): List<OrderItemRequest> =
        quantities.map { (menuItemId, quantity) -> OrderItemRequest(menuItemId = menuItemId, quantity = quantity) }
}
