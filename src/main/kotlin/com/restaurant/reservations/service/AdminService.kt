package com.restaurant.reservations.service

import com.restaurant.reservations.dto.OrderItemResponse
import com.restaurant.reservations.dto.OrderResponse
import com.restaurant.reservations.dto.ReservationResponse
import com.restaurant.reservations.exception.ValidationBusinessException
import com.restaurant.reservations.model.Order
import com.restaurant.reservations.model.OrderStatus
import com.restaurant.reservations.model.Reservation
import com.restaurant.reservations.repository.OrderRepository
import com.restaurant.reservations.repository.ReservationRepository
import org.springframework.stereotype.Service

/**
 * Vistas globales del panel de administracion, acotadas al restaurante del admin.
 *
 * ADMIN es el administrador de UN restaurante, no un superusuario: antes estas
 * consultas usaban findAll() y exponian pedidos y reservas (con nombres de
 * clientes) de todos los restaurantes (QA-SEC-01).
 */
@Service
class AdminService(
    private val reservationRepository: ReservationRepository,
    private val orderRepository: OrderRepository,
    private val authService: AuthService
) {

    fun getAllReservations(): List<ReservationResponse> =
        reservationRepository.findByTableRestaurantId(currentRestaurantId())
            .map { toResponse(it) }

    fun getAllOrders(): List<OrderResponse> =
        orderRepository.findByRestaurantId(currentRestaurantId())
            .map { toResponse(it) }

    fun getActiveOrders(): List<OrderResponse> =
        orderRepository.findByRestaurantIdAndStatus(currentRestaurantId(), OrderStatus.IN_PROGRESS)
            .map { toResponse(it) }

    private fun currentRestaurantId(): Long =
        authService.getCurrentUserRestaurantId()
            ?: throw ValidationBusinessException("User not associated with a restaurant")

    private fun toResponse(reservation: Reservation): ReservationResponse =
        ReservationResponse(
            id = reservation.id!!,
            tableId = reservation.table.id!!,
            customerId = reservation.customer.id!!,
            customerName = reservation.customer.name,
            reservationDate = reservation.reservationDate,
            numberOfGuests = reservation.numberOfGuests,
            status = reservation.status.name,
            createdAt = reservation.createdAt,
            specialRequests = reservation.specialRequests,
            confirmed = reservation.confirmed
        )

    private fun toResponse(order: Order): OrderResponse =
        OrderResponse(
            id = order.id!!,
            tableId = order.table.id!!,
            tableNumber = order.table.tableNumber,
            employeeId = order.employee.id!!,
            employeeName = order.employee.name,
            orderDate = order.orderDate,
            status = order.status.name,
            totalAmount = order.totalAmount,
            items = order.items.map { item ->
                OrderItemResponse(
                    id = item.id!!,
                    menuItemId = item.menuItem?.id,
                    itemName = item.itemName,
                    quantity = item.quantity,
                    price = item.price,
                    status = item.status.name,
                    notes = item.notes
                )
<<<<<<< HEAD
            }
    }
    
    fun getAllOrders(): List<OrderResponse> {
        return orderRepository.findAll()
            .map { order ->
                OrderResponse(
                    id = order.id!!,
                    tableId = order.table.id!!,
                    tableNumber = order.table.tableNumber,
                    employeeId = order.employee.id!!,
                    employeeName = order.employee.name,
                    orderDate = order.orderDate,
                    status = order.status.name,
                    totalAmount = order.totalAmount,
                    items = order.items.map { item ->
                        com.restaurant.reservations.dto.OrderItemResponse(
                            id = item.id!!,
                            menuItemId = item.menuItem?.id,
                            itemName = item.itemName,
                            quantity = item.quantity,
                            price = item.price,
                            status = item.status.name,
                            notes = item.notes
                        )
                    },
                    notes = order.notes
                )
            }
    }
    
    fun getActiveOrders(): List<OrderResponse> {
        return orderRepository.findByStatus(com.restaurant.reservations.model.OrderStatus.IN_PROGRESS)
            .map { order ->
                OrderResponse(
                    id = order.id!!,
                    tableId = order.table.id!!,
                    tableNumber = order.table.tableNumber,
                    employeeId = order.employee.id!!,
                    employeeName = order.employee.name,
                    orderDate = order.orderDate,
                    status = order.status.name,
                    totalAmount = order.totalAmount,
                    items = order.items.map { item ->
                        com.restaurant.reservations.dto.OrderItemResponse(
                            id = item.id!!,
                            menuItemId = item.menuItem?.id,
                            itemName = item.itemName,
                            quantity = item.quantity,
                            price = item.price,
                            status = item.status.name,
                            notes = item.notes
                        )
                    },
                    notes = order.notes
                )
            }
    }
=======
            },
            notes = order.notes
        )
>>>>>>> ed340704ed016e6dcc9e8c59c76220b3ee9c292e
}
