package com.restaurant.reservations.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record TableRequest(
    @NotNull
    @Min(1)
    Integer tableNumber,

    @jakarta.validation.constraints.Size(max = 120)
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
    Integer gridY,

    // Grupo (area/sede) responsable; null = cualquier mesero del restaurante.
    Long groupId,

    @Min(0)
    Integer sortOrder,

    // AVAILABLE | MAINTENANCE (estado operativo; la ocupacion la llevan las sesiones)
    String status
) {
    public TableRequest {
        if (gridX == null) {
            gridX = 1;
        }
        if (gridY == null) {
            gridY = 1;
        }
        if (sortOrder == null) {
            sortOrder = 0;
        }
    }
}
