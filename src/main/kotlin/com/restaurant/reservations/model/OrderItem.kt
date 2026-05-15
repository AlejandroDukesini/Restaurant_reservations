package com.restaurant.reservations.model

import jakarta.persistence.*

@Entity
@Table(name = "order_items")
data class OrderItem(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    
    @ManyToOne
    @JoinColumn(name = "order_id", nullable = false)
    val order: Order,
    
    @Column(nullable = false)
    val itemName: String,
    
    @Column(nullable = false)
    val quantity: Int,
    
    @Column(nullable = false)
    val price: Double,
    
    @Column
    val notes: String? = null
)
