package com.restaurant.reservations;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
// Latido periodico del canal en tiempo real (RealtimeService.heartbeat).
@EnableScheduling
public class RestaurantReservationsApplication {

    public static void main(String[] args) {
        SpringApplication.run(RestaurantReservationsApplication.class, args);
    }
}
