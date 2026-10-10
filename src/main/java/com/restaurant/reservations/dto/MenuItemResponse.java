package com.restaurant.reservations.dto;

public record MenuItemResponse(
    long id,
    String name,
    String description,
    String category,
    double price,
    String protein,
    String condiments,
    String ingredients,
    String preparationNotes,
    boolean active
) {
}
