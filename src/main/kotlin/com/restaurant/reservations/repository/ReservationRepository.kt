package com.restaurant.reservations.repository

import com.restaurant.reservations.model.Reservation
import com.restaurant.reservations.model.ReservationStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.util.Optional

@Repository
interface ReservationRepository : JpaRepository<Reservation, Long> {
    fun findByCustomerId(customerId: Long): List<Reservation>
    fun findByTableId(tableId: Long): List<Reservation>
    fun findByTableRestaurantId(restaurantId: Long): List<Reservation>
    fun findByTableIdAndReservationDateBetween(
        tableId: Long,
        startDate: LocalDateTime,
        endDate: LocalDateTime
    ): List<Reservation>
    fun findByIdAndCustomerId(id: Long, customerId: Long): Optional<Reservation>

    @Query(
        """
        select r.table.id from Reservation r
        where r.table.restaurant.id = :restaurantId
          and r.status in :blockingStatuses
          and r.reservationDate < :endAt
          and r.reservationEnd > :startAt
        """
    )
    fun findReservedTableIds(
        @Param("restaurantId") restaurantId: Long,
        @Param("startAt") startAt: LocalDateTime,
        @Param("endAt") endAt: LocalDateTime,
        @Param("blockingStatuses") blockingStatuses: Collection<ReservationStatus>
    ): List<Long>

    @Query(
        """
        select count(r) > 0 from Reservation r
        where r.table.id = :tableId
          and r.status in :blockingStatuses
          and r.reservationDate < :endAt
          and r.reservationEnd > :startAt
        """
    )
    fun existsOverlappingReservation(
        @Param("tableId") tableId: Long,
        @Param("startAt") startAt: LocalDateTime,
        @Param("endAt") endAt: LocalDateTime,
        @Param("blockingStatuses") blockingStatuses: Collection<ReservationStatus>
    ): Boolean

    @Query(
        """
        select count(r) > 0 from Reservation r
        where r.table.id = :tableId
          and r.id <> :reservationId
          and r.status in :blockingStatuses
          and r.reservationDate < :endAt
          and r.reservationEnd > :startAt
        """
    )
    fun existsOverlappingReservationExcluding(
        @Param("reservationId") reservationId: Long,
        @Param("tableId") tableId: Long,
        @Param("startAt") startAt: LocalDateTime,
        @Param("endAt") endAt: LocalDateTime,
        @Param("blockingStatuses") blockingStatuses: Collection<ReservationStatus>
    ): Boolean
}
