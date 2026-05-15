package com.restaurant.reservations.service

import com.restaurant.reservations.dto.ReservationRequest
import com.restaurant.reservations.dto.ReservationResponse
import com.restaurant.reservations.model.Reservation
import com.restaurant.reservations.model.ReservationStatus
import com.restaurant.reservations.repository.ReservationRepository
import com.restaurant.reservations.repository.TableRepository
import com.restaurant.reservations.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ReservationService(
    private val reservationRepository: ReservationRepository,
    private val tableRepository: TableRepository,
    private val userRepository: UserRepository,
    private val authService: AuthService
) {
    
    @Transactional
    fun createReservation(request: ReservationRequest): ReservationResponse {
        val customerId = authService.getCurrentUserId()
        
        val table = tableRepository.findById(request.tableId)
            .orElseThrow { IllegalArgumentException("Table not found") }
        
        val customer = userRepository.findById(customerId)
            .orElseThrow { IllegalArgumentException("Customer not found") }
        
        if (request.numberOfGuests > table.capacity) {
            throw IllegalArgumentException("Number of guests exceeds table capacity")
        }
        
        val reservation = Reservation(
            table = table,
            customer = customer,
            reservationDate = request.reservationDate,
            numberOfGuests = request.numberOfGuests,
            specialRequests = request.specialRequests
        )
        
        return toResponse(reservationRepository.save(reservation))
    }
    
    fun getMyReservations(): List<ReservationResponse> {
        val customerId = authService.getCurrentUserId()
        return reservationRepository.findByCustomerId(customerId)
            .map { toResponse(it) }
    }
    
    fun getReservationsByTable(tableId: Long): List<ReservationResponse> {
        return reservationRepository.findByTableId(tableId)
            .map { toResponse(it) }
    }
    
    fun getReservationById(id: Long): ReservationResponse {
        val reservation = reservationRepository.findById(id)
            .orElseThrow { IllegalArgumentException("Reservation not found") }
        return toResponse(reservation)
    }
    
    @Transactional
    fun updateReservation(id: Long, request: ReservationRequest): ReservationResponse {
        val reservation = reservationRepository.findById(id)
            .orElseThrow { IllegalArgumentException("Reservation not found") }
        
        val customerId = authService.getCurrentUserId()
        if (reservation.customer.id != customerId && authService.getCurrentUserRole().name != "ADMIN") {
            throw IllegalArgumentException("Unauthorized access to reservation")
        }
        
        val table = tableRepository.findById(request.tableId)
            .orElseThrow { IllegalArgumentException("Table not found") }
        
        val updatedReservation = reservation.copy(
            table = table,
            reservationDate = request.reservationDate,
            numberOfGuests = request.numberOfGuests,
            specialRequests = request.specialRequests
        )
        
        return toResponse(reservationRepository.save(updatedReservation))
    }
    
    @Transactional
    fun confirmReservation(id: Long): ReservationResponse {
        val reservation = reservationRepository.findById(id)
            .orElseThrow { IllegalArgumentException("Reservation not found") }
        
        val updatedReservation = reservation.copy(
            status = ReservationStatus.CONFIRMED,
            confirmed = true
        )
        
        return toResponse(reservationRepository.save(updatedReservation))
    }
    
    @Transactional
    fun cancelReservation(id: Long): ReservationResponse {
        val reservation = reservationRepository.findById(id)
            .orElseThrow { IllegalArgumentException("Reservation not found") }
        
        val customerId = authService.getCurrentUserId()
        if (reservation.customer.id != customerId && authService.getCurrentUserRole().name != "ADMIN") {
            throw IllegalArgumentException("Unauthorized access to reservation")
        }
        
        val updatedReservation = reservation.copy(
            status = ReservationStatus.CANCELLED
        )
        
        return toResponse(reservationRepository.save(updatedReservation))
    }
    
    @Transactional
    fun deleteReservation(id: Long) {
        val reservation = reservationRepository.findById(id)
            .orElseThrow { IllegalArgumentException("Reservation not found") }
        
        reservationRepository.delete(reservation)
    }
    
    private fun toResponse(reservation: Reservation): ReservationResponse {
        return ReservationResponse(
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
