package com.restaurant.reservations.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record OrderItemRequest(
    @NotNull
    Long menuItemId,

    // Techo por plato: sin el, una cantidad enorme desvirtua el total del pedido
    // y la cola de cocina.
    @NotNull
    @Min(1)
    @Max(500)
    Integer quantity,

    @Size(max = 500)
    String notes
) {
}
