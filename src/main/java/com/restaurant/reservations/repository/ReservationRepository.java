package com.restaurant.reservations.repository;

import com.restaurant.reservations.model.Reservation;
import com.restaurant.reservations.model.ReservationStatus;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, Long> {
    List<Reservation> findByCustomerId(Long customerId);

    List<Reservation> findByTableId(Long tableId);

    List<Reservation> findByTableRestaurantId(Long restaurantId);

    List<Reservation> findByTableIdAndReservationDateBetween(
        Long tableId,
        LocalDateTime startDate,
        LocalDateTime endDate
    );

    Optional<Reservation> findByIdAndCustomerId(Long id, Long customerId);

    @Query("""
        select r.table.id from Reservation r
        where r.table.restaurant.id = :restaurantId
          and r.status in :blockingStatuses
          and r.reservationDate < :endAt
          and r.reservationEnd > :startAt
        """)
    List<Long> findReservedTableIds(
        @Param("restaurantId") Long restaurantId,
        @Param("startAt") LocalDateTime startAt,
        @Param("endAt") LocalDateTime endAt,
        @Param("blockingStatuses") Collection<ReservationStatus> blockingStatuses
    );

    @Query("""
        select count(r) > 0 from Reservation r
        where r.table.id = :tableId
          and r.status in :blockingStatuses
          and r.reservationDate < :endAt
          and r.reservationEnd > :startAt
        """)
    boolean existsOverlappingReservation(
        @Param("tableId") Long tableId,
        @Param("startAt") LocalDateTime startAt,
        @Param("endAt") LocalDateTime endAt,
        @Param("blockingStatuses") Collection<ReservationStatus> blockingStatuses
    );

    @Query("""
        select count(r) > 0 from Reservation r
        where r.table.id = :tableId
          and r.id <> :reservationId
          and r.status in :blockingStatuses
          and r.reservationDate < :endAt
          and r.reservationEnd > :startAt
        """)
    boolean existsOverlappingReservationExcluding(
        @Param("reservationId") Long reservationId,
        @Param("tableId") Long tableId,
        @Param("startAt") LocalDateTime startAt,
        @Param("endAt") LocalDateTime endAt,
        @Param("blockingStatuses") Collection<ReservationStatus> blockingStatuses
    );
}
