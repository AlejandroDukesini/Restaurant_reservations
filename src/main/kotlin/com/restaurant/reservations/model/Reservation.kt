package com.restaurant.reservations.model

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(
    name = "reservations",
    indexes = [
        Index(name = "idx_reservation_table_date_status", columnList = "table_id,reservation_date,status")
    ]
)
data class Reservation(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    
    @ManyToOne
    @JoinColumn(name = "table_id", nullable = false)
    val table: RestaurantTable,
    
    @ManyToOne
    @JoinColumn(name = "customer_id", nullable = false)
    val customer: User,
    
    @Column(nullable = false)
    val reservationDate: LocalDateTime,

    @Column(nullable = false)
    val reservationEnd: LocalDateTime = reservationDate.plusHours(2),
    
    @Column(nullable = false)
    val numberOfGuests: Int,
    
    @Column(nullable = false)
    val status: ReservationStatus = ReservationStatus.PENDING,
    
    @Column(nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
    
    @Column
    val specialRequests: String? = null,
    
    @Column(nullable = false)
    val confirmed: Boolean = false
)

enum class ReservationStatus {
    PENDING,
    CONFIRMED,
    CANCELLED,
    COMPLETED
}
