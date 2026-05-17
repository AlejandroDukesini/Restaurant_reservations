package com.restaurant.reservations.service

import com.restaurant.reservations.dto.OrderRequest
import com.restaurant.reservations.dto.OrderResponse
import com.restaurant.reservations.model.Order
import com.restaurant.reservations.model.OrderItem
import com.restaurant.reservations.model.OrderStatus
import com.restaurant.reservations.repository.OrderItemRepository
import com.restaurant.reservations.repository.OrderRepository
import com.restaurant.reservations.repository.TableRepository
import com.restaurant.reservations.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class OrderService(
    private val orderRepository: OrderRepository,
    private val orderItemRepository: OrderItemRepository,
    private val tableRepository: TableRepository,
    private val userRepository: UserRepository,
    private val authService: AuthService
) {
    
    @Transactional
    fun createOrder(request: OrderRequest): OrderResponse {
        val employeeId = authService.getCurrentUserId()
        
        val table = tableRepository.findById(request.tableId)
            .orElseThrow { IllegalArgumentException("Table not found") }
        
        val employee = userRepository.findById(employeeId)
            .orElseThrow { IllegalArgumentException("Employee not found") }
        
        val orderItems = request.items.map { itemRequest ->
            OrderItem(
                order = null, // Will be set after order is saved
                itemName = itemRequest.itemName,
                quantity = itemRequest.quantity,
                price = itemRequest.price,
                notes = itemRequest.notes
            )
        }
        
        val totalAmount = orderItems.sumOf { it.price * it.quantity }
        
        val order = Order(
            table = table,
            employee = employee,
            totalAmount = totalAmount,
            notes = request.notes
        )
        
        val savedOrder = orderRepository.save(order)
        
        val savedOrderItems = orderItems.map { it.copy(order = savedOrder) }
        orderItemRepository.saveAll(savedOrderItems)
        
        return toResponse(savedOrder.copy(items = savedOrderItems))
    }
    
    fun getMyOrders(): List<OrderResponse> {
        val employeeId = authService.getCurrentUserId()
        return orderRepository.findByEmployeeId(employeeId)
            .map { toResponse(it) }
    }
    
    fun getOrdersByTable(tableId: Long): List<OrderResponse> {
        return orderRepository.findByTableId(tableId)
            .map { toResponse(it) }
    }
    
    fun getOrderById(id: Long): OrderResponse {
        val order = orderRepository.findById(id)
            .orElseThrow { IllegalArgumentException("Order not found") }
        return toResponse(order)
    }
    
    @Transactional
    fun updateOrderStatus(id: Long, status: String): OrderResponse {
        val order = orderRepository.findById(id)
            .orElseThrow { IllegalArgumentException("Order not found") }
        
        val employeeId = authService.getCurrentUserId()
        if (order.employee.id != employeeId && authService.getCurrentUserRole().name != "ADMIN") {
            throw IllegalArgumentException("Unauthorized access to order")
        }
        
        val updatedOrder = order.copy(
            status = OrderStatus.valueOf(status.uppercase())
        )
        
        return toResponse(orderRepository.save(updatedOrder))
    }
    
    @Transactional
    fun deleteOrder(id: Long) {
        val order = orderRepository.findById(id)
            .orElseThrow { IllegalArgumentException("Order not found") }
        
        val employeeId = authService.getCurrentUserId()
        if (order.employee.id != employeeId && authService.getCurrentUserRole().name != "ADMIN") {
            throw IllegalArgumentException("Unauthorized access to order")
        }
        
        orderRepository.delete(order)
    }
    
    private fun toResponse(order: Order): OrderResponse {
        return OrderResponse(
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
