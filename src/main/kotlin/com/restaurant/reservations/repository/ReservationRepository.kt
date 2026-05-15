package com.restaurant.reservations.repository

import com.restaurant.reservations.model.Reservation
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.util.Optional

@Repository
interface ReservationRepository : JpaRepository<Reservation, Long> {
    fun findByCustomerId(customerId: Long): List<Reservation>
    fun findByTableId(tableId: Long): List<Reservation>
    fun findByRestaurantId(restaurantId: Long): List<Reservation>
    fun findByTableIdAndReservationDateBetween(
        tableId: Long,
        startDate: LocalDateTime,
        endDate: LocalDateTime
    ): List<Reservation>
    fun findByIdAndCustomerId(id: Long, customerId: Long): Optional<Reservation>
}
