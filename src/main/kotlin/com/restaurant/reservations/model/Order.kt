package com.restaurant.reservations.model

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "orders")
data class Order(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    
    @ManyToOne
    @JoinColumn(name = "table_id", nullable = false)
    val table: RestaurantTable,
    
    @ManyToOne
    @JoinColumn(name = "employee_id", nullable = false)
    val employee: User,
    
    @Column(nullable = false)
    val orderDate: LocalDateTime = LocalDateTime.now(),
    
    @Column(nullable = false)
    val status: OrderStatus = OrderStatus.PENDING,
    
    @Column(nullable = false)
    val totalAmount: Double = 0.0,
    
    @OneToMany(mappedBy = "order", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    val items: List<OrderItem> = emptyList(),
    
    @Column
    val notes: String? = null
)

enum class OrderStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED
}
