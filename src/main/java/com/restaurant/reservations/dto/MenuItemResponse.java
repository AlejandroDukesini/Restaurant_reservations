package com.restaurant.reservations.dto;

/** "category" es ahora el nombre de la categoria libre (antes un codigo fijo). */
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
    boolean active,
    Long categoryId,
    String imageMediaId,
    String imageUrl,
    Integer prepMinutes,
    boolean available
) {
}
