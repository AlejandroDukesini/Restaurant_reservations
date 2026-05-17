package com.restaurant.reservations.model

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "restaurants")
data class Restaurant(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    
    @Column(unique = true, nullable = false)
    val name: String,
    
    @Column(unique = true, nullable = false)
    val slug: String,
    
    @Column(nullable = false)
    val description: String,
    
    @Column(nullable = false)
    val address: String,
    
    @Column(nullable = false)
    val phone: String,
    
    @Column(nullable = false)
    val email: String,
    
    @Column(nullable = false)
    val numberOfTables: Int,
    
    @Column(nullable = false)
    val numberOfChairs: Int,
    
    @Column(nullable = false)
    val numberOfFloors: Int,
    
    @Column(nullable = false)
    val websiteUrl: String? = null,
    
    @Column(nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
    
    @Column(nullable = false)
    val active: Boolean = true,
    
    @OneToMany(mappedBy = "restaurant", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    val tables: List<RestaurantTable> = emptyList(),
    
    @OneToMany(mappedBy = "restaurant", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    val users: List<User> = emptyList()
)
