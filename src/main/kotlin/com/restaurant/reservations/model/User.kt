package com.restaurant.reservations.model

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "users")
data class User(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    
    @Column(unique = true, nullable = false)
    val email: String,
    
    @Column(nullable = false)
    val password: String,
    
    @Column(nullable = false)
    val name: String,
    
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    val role: Role,
    
    @Column(nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
    
    @ManyToOne
    @JoinColumn(name = "restaurant_id")
    val restaurant: Restaurant? = null,
    
    @Column(nullable = false)
    val active: Boolean = true
)
