package com.restaurant.reservations.controller

import com.restaurant.reservations.dto.OrderRequest
import com.restaurant.reservations.dto.OrderResponse
import com.restaurant.reservations.service.OrderService
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api")
class OrderController(
    private val orderService: OrderService
) {
    
    @PostMapping("/employee/orders")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    fun createOrder(@RequestBody request: OrderRequest): ResponseEntity<OrderResponse> {
        val order = orderService.createOrder(request)
        return ResponseEntity.ok(order)
    }
    
    @GetMapping("/employee/orders")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    fun getMyOrders(): ResponseEntity<List<OrderResponse>> {
        val orders = orderService.getMyOrders()
        return ResponseEntity.ok(orders)
    }
    
    @GetMapping("/employee/tables/{tableId}/orders")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    fun getOrdersByTable(@PathVariable tableId: Long): ResponseEntity<List<OrderResponse>> {
        val orders = orderService.getOrdersByTable(tableId)
        return ResponseEntity.ok(orders)
    }
    
    @GetMapping("/employee/orders/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    fun getOrderById(@PathVariable id: Long): ResponseEntity<OrderResponse> {
        val order = orderService.getOrderById(id)
        return ResponseEntity.ok(order)
    }
    
    @PutMapping("/employee/orders/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    fun updateOrderStatus(
        @PathVariable id: Long,
        @RequestParam status: String
    ): ResponseEntity<OrderResponse> {
        val order = orderService.updateOrderStatus(id, status)
        return ResponseEntity.ok(order)
    }
    
    @DeleteMapping("/employee/orders/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    fun deleteOrder(@PathVariable id: Long): ResponseEntity<Void> {
        orderService.deleteOrder(id)
        return ResponseEntity.noContent().build()
    }
}
