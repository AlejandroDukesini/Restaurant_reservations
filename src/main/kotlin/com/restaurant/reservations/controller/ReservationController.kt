package com.restaurant.reservations.controller

import com.restaurant.reservations.dto.ReservationRequest
import com.restaurant.reservations.dto.ReservationResponse
import com.restaurant.reservations.dto.PublicReservationRequest
import com.restaurant.reservations.dto.TableMapResponse
import com.restaurant.reservations.service.ReservationService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.*
import java.time.LocalDate
import java.time.LocalTime

@RestController
@RequestMapping("/api")
class ReservationController(
    private val reservationService: ReservationService
) {
    @GetMapping("/public/restaurants/{restaurantId}/table-map")
    fun getTableMap(
        @PathVariable restaurantId: Long,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) time: LocalTime
    ): ResponseEntity<TableMapResponse> {
        return ResponseEntity.ok(reservationService.getTableMap(restaurantId, date, time))
    }

    @PostMapping("/public/reservations")
    fun createPublicReservation(@Valid @RequestBody request: PublicReservationRequest): ResponseEntity<ReservationResponse> {
        return ResponseEntity.ok(reservationService.createPublicReservation(request))
    }
    
    @PostMapping("/customer/reservations")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE', 'CUSTOMER')")
    fun createReservation(@Valid @RequestBody request: ReservationRequest): ResponseEntity<ReservationResponse> {
        val reservation = reservationService.createReservation(request)
        return ResponseEntity.ok(reservation)
    }
    
    @GetMapping("/customer/reservations")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE', 'CUSTOMER')")
    fun getMyReservations(): ResponseEntity<List<ReservationResponse>> {
        val reservations = reservationService.getMyReservations()
        return ResponseEntity.ok(reservations)
    }
    
    @GetMapping("/public/tables/{tableId}/reservations")
    fun getReservationsByTable(@PathVariable tableId: Long): ResponseEntity<List<ReservationResponse>> {
        val reservations = reservationService.getReservationsByTable(tableId)
        return ResponseEntity.ok(reservations)
    }
    
    @GetMapping("/customer/reservations/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE', 'CUSTOMER')")
    fun getReservationById(@PathVariable id: Long): ResponseEntity<ReservationResponse> {
        val reservation = reservationService.getReservationById(id)
        return ResponseEntity.ok(reservation)
    }
    
    @PutMapping("/customer/reservations/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE', 'CUSTOMER')")
    fun updateReservation(
        @PathVariable id: Long,
        @Valid @RequestBody request: ReservationRequest
    ): ResponseEntity<ReservationResponse> {
        val reservation = reservationService.updateReservation(id, request)
        return ResponseEntity.ok(reservation)
    }
    
    @PutMapping("/customer/reservations/{id}/confirm")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE', 'CUSTOMER')")
    fun confirmReservation(@PathVariable id: Long): ResponseEntity<ReservationResponse> {
        val reservation = reservationService.confirmReservation(id)
        return ResponseEntity.ok(reservation)
    }
    
    @PutMapping("/customer/reservations/{id}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE', 'CUSTOMER')")
    fun cancelReservation(@PathVariable id: Long): ResponseEntity<ReservationResponse> {
        val reservation = reservationService.cancelReservation(id)
        return ResponseEntity.ok(reservation)
    }
    
    @DeleteMapping("/customer/reservations/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE', 'CUSTOMER')")
    fun deleteReservation(@PathVariable id: Long): ResponseEntity<Void> {
        reservationService.deleteReservation(id)
        return ResponseEntity.noContent().build()
    }
}
