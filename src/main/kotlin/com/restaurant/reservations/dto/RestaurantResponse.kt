package com.restaurant.reservations.dto

data class RestaurantResponse(
    val id: Long,
    val name: String,
    val slug: String,
    val description: String,
    val address: String,
    val phone: String,
    val email: String,
    val numberOfTables: Int,
    val numberOfChairs: Int,
    val numberOfFloors: Int,
    val websiteUrl: String?,
    val createdAt: String,
    val active: Boolean
)
