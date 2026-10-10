package com.restaurant.reservations.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record TableRequest(
    @NotNull
    @Min(1)
    Integer tableNumber,

    String name,

    @NotNull
    @Min(1)
    Integer floor,

    @NotNull
    @Min(1)
    Integer capacity,

    @NotNull
    @PositiveOrZero
    Double price,

    Long zoneId,

    @Min(1)
    Integer gridX,

    @Min(1)
    Integer gridY
) {
    public TableRequest {
        if (gridX == null) {
            gridX = 1;
        }
        if (gridY == null) {
            gridY = 1;
        }
    }
}
