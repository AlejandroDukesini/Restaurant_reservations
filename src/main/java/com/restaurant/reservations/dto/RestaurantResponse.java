package com.restaurant.reservations.dto;

public record RestaurantResponse(
    long id,
    String name,
    String slug,
    String description,
    String address,
    String phone,
    String email,
    int numberOfTables,
    int numberOfChairs,
    int numberOfFloors,
    String websiteUrl,
    String createdAt,
    boolean active
) {
}
