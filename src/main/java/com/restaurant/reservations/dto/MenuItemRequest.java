package com.restaurant.reservations.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record MenuItemRequest(
    @NotBlank
    @Size(max = 255)
    String name,

    @Size(max = 500)
    String description,

    /**
     * Nombre de categoria (compatibilidad con clientes anteriores: STARTER, MAIN, DESSERT,
     * DRINK o cualquier nombre existente). Se usa solo si no llega categoryId.
     */
    @Size(max = 80)
    String category,

    @NotNull
    @PositiveOrZero
    @Max(100_000_000)
    Double price,

    @Size(max = 255)
    String protein,
    @Size(max = 1000)
    String condiments,
    @Size(max = 2000)
    String ingredients,
    @Size(max = 1000)
    String preparationNotes,

    Long categoryId,

    @Size(max = 36)
    String imageMediaId,

    @Min(0)
    @Max(600)
    Integer prepMinutes,

    Boolean available
) {
}
