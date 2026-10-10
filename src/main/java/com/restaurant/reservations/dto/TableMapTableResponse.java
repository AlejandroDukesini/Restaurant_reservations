package com.restaurant.reservations.dto;

public record TableMapTableResponse(
    long id,
    int tableNumber,
    int capacity,
    int gridX,
    int gridY,
    String zoneName,
    String availability
) {
}
