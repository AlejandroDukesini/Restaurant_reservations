package com.restaurant.reservations.model

import jakarta.persistence.*

@Entity
@Table(name = "tables")
data class Table(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    
    @Column(nullable = false)
    val tableNumber: Int,
    
    @Column(nullable = false)
    val floor: Int,
    
    @Column(nullable = false)
    val capacity: Int,
    
    @Column(nullable = false)
    val price: Double,
    
    @ManyToOne
    @JoinColumn(name = "restaurant_id", nullable = false)
    val restaurant: Restaurant,
    
    @Column(nullable = false)
    val active: Boolean = true,
    
    @OneToMany(mappedBy = "table", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    val reservations: List<Reservation> = emptyList(),
    
    @OneToMany(mappedBy = "table", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    val orders: List<Order> = emptyList()
)
