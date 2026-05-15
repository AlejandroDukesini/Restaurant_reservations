package com.restaurant.reservations.controller

import com.restaurant.reservations.dto.OrderResponse
import com.restaurant.reservations.dto.ReservationResponse
import com.restaurant.reservations.service.AdminService
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
class AdminController(
    private val adminService: AdminService
) {
    
    @GetMapping("/reservations")
    fun getAllReservations(): ResponseEntity<List<ReservationResponse>> {
        val reservations = adminService.getAllReservations()
        return ResponseEntity.ok(reservations)
    }
    
    @GetMapping("/orders")
    fun getAllOrders(): ResponseEntity<List<OrderResponse>> {
        val orders = adminService.getAllOrders()
        return ResponseEntity.ok(orders)
    }
    
    @GetMapping("/orders/active")
    fun getActiveOrders(): ResponseEntity<List<OrderResponse>> {
        val orders = adminService.getActiveOrders()
        return ResponseEntity.ok(orders)
    }
}
