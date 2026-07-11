package com.restaurant.reservations.service

import com.restaurant.reservations.dto.CookQueueItem
import com.restaurant.reservations.model.Order
import com.restaurant.reservations.model.OrderItem
import com.restaurant.reservations.model.OrderItemStatus
import com.restaurant.reservations.model.OrderStatus
import com.restaurant.reservations.repository.OrderItemRepository
import com.restaurant.reservations.repository.OrderRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CookService(
    private val orderRepository: OrderRepository,
    private val orderItemRepository: OrderItemRepository,
    private val authService: AuthService
) {

    private val activeOrderStatuses = listOf(OrderStatus.PENDING, OrderStatus.IN_PROGRESS)

    // Cola de cocina: todos los platos aún no listos de los pedidos activos del restaurante.
    fun getQueue(): List<CookQueueItem> {
        val restaurantId = currentRestaurantId()
        return orderRepository.findActiveByRestaurant(restaurantId, activeOrderStatuses)
            .flatMap { order ->
                order.items
                    .filter { it.status != OrderItemStatus.READY }
                    .map { item -> toQueueItem(order, item) }
            }
    }

    @Transactional
    fun updateItemStatus(orderItemId: Long, status: String): CookQueueItem {
        val restaurantId = currentRestaurantId()
        val item = orderItemRepository.findById(orderItemId)
            .orElseThrow { IllegalArgumentException("Order item not found") }

        val order = item.order
            ?: throw IllegalArgumentException("Order item is not attached to an order")

        if (order.table.restaurant.id != restaurantId) {
            throw IllegalArgumentException("Unauthorized access to order item")
        }

        val newStatus = try {
            OrderItemStatus.valueOf(status.uppercase())
        } catch (ex: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid item status: $status")
        }

        val savedItem = orderItemRepository.save(item.copy(status = newStatus))
        syncOrderStatus(order.id!!)

        return toQueueItem(order, savedItem)
    }

    // Recalcula el estado del pedido a partir de sus platos.
    @Transactional
    fun syncOrderStatus(orderId: Long) {
        val order = orderRepository.findById(orderId)
            .orElseThrow { IllegalArgumentException("Order not found") }
        val items = order.items
        if (items.isEmpty()) return

        val newStatus = when {
            items.all { it.status == OrderItemStatus.READY } -> OrderStatus.COMPLETED
            items.any { it.status != OrderItemStatus.PENDING } -> OrderStatus.IN_PROGRESS
            else -> OrderStatus.PENDING
        }

        if (newStatus != order.status && order.status != OrderStatus.CANCELLED) {
            orderRepository.save(order.copy(status = newStatus))
        }
    }

    private fun currentRestaurantId(): Long =
        authService.getCurrentUserRestaurantId()
            ?: throw IllegalArgumentException("User not associated with a restaurant")

    private fun toQueueItem(order: Order, item: OrderItem): CookQueueItem =
        CookQueueItem(
            orderItemId = item.id!!,
            orderId = order.id!!,
            tableId = order.table.id!!,
            tableNumber = order.table.tableNumber,
            tableName = order.table.name,
            employeeName = order.employee.name,
            orderDate = order.orderDate,
            itemName = item.itemName,
            quantity = item.quantity,
            status = item.status.name,
            notes = item.notes,
            category = item.menuItem?.category?.name,
            protein = item.menuItem?.protein,
            condiments = item.menuItem?.condiments,
            ingredients = item.menuItem?.ingredients,
            preparationNotes = item.menuItem?.preparationNotes
        )
}
