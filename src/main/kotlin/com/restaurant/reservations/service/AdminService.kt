package com.restaurant.reservations.service

import com.restaurant.reservations.dto.ReservationResponse
import com.restaurant.reservations.dto.OrderResponse
import com.restaurant.reservations.repository.ReservationRepository
import com.restaurant.reservations.repository.OrderRepository
import org.springframework.stereotype.Service

@Service
class AdminService(
    private val reservationRepository: ReservationRepository,
    private val orderRepository: OrderRepository
) {
    
    fun getAllReservations(): List<ReservationResponse> {
        return reservationRepository.findAll()
            .map { reservation ->
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
                            itemName = item.itemName,
                            quantity = item.quantity,
                            price = item.price,
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
                            itemName = item.itemName,
                            quantity = item.quantity,
                            price = item.price,
                            notes = item.notes
                        )
                    },
                    notes = order.notes
                )
            }
    }
}
