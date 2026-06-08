package com.restaurant.reservations.model

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.Table

@Entity
@Table(name = "zones")
data class Zone(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false)
    val name: String,

    @Column(nullable = false)
    val code: String,

    @Column(nullable = false)
    val sortOrder: Int = 0,

    @ManyToOne
    @JoinColumn(name = "restaurant_id", nullable = false)
    val restaurant: Restaurant,

    @Column(nullable = false)
    val active: Boolean = true,

    @OneToMany(mappedBy = "zone", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    val tables: List<RestaurantTable> = emptyList()
)
