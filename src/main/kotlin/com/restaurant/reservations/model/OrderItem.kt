package com.restaurant.reservations.model

import jakarta.persistence.*

@Entity
@Table(name = "order_items")
data class OrderItem(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    
    @ManyToOne
    @JoinColumn(name = "order_id")
    val order: Order? = null,

    @ManyToOne
    @JoinColumn(name = "menu_item_id")
    val menuItem: MenuItem? = null,

    @Column(nullable = false)
    val itemName: String,

    @Column(nullable = false)
    val quantity: Int,

    @Column(nullable = false)
    val price: Double,

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    val status: OrderItemStatus = OrderItemStatus.PENDING,

    @Column
    val notes: String? = null
)

enum class OrderItemStatus {
    PENDING,
    PREPARING,
    READY
}
