package com.restaurant.reservations.model

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "reservations")
data class Reservation(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    
    @ManyToOne
    @JoinColumn(name = "table_id", nullable = false)
    val table: Table,
    
    @ManyToOne
    @JoinColumn(name = "customer_id", nullable = false)
    val customer: User,
    
    @Column(nullable = false)
    val reservationDate: LocalDateTime,
    
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
