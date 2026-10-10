package com.restaurant.reservations.dto;

public record TableResponse(
    long id,
    int tableNumber,
    String name,
    int floor,
    int capacity,
    double price,
    long restaurantId,
    boolean active,
    Long zoneId,
    String zoneName,
    int gridX,
    int gridY,
    String status
) {
}
