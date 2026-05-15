package com.restaurant.reservations.dto

data class RestaurantRegistrationRequest(
    val name: String,
    val description: String,
    val address: String,
    val phone: String,
    val email: String,
    val numberOfTables: Int,
    val numberOfChairs: Int,
    val numberOfFloors: Int,
    val adminEmail: String,
    val adminPassword: String,
    val adminName: String
)
