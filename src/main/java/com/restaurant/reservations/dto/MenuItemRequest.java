package com.restaurant.reservations.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record MenuItemRequest(
    @NotBlank
    String name,

    String description,

    // STARTER | MAIN | DESSERT | DRINK
    String category,

    @NotNull
    @PositiveOrZero
    Double price,

    String protein,
    String condiments,
    String ingredients,
    String preparationNotes
) {
    public MenuItemRequest {
        if (category == null) {
            category = "MAIN";
        }
    }
}
