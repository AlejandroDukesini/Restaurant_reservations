package com.restaurant.reservations

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class RestaurantReservationsApplication

fun main(args: Array<String>) {
    runApplication<RestaurantReservationsApplication>(*args)
}
