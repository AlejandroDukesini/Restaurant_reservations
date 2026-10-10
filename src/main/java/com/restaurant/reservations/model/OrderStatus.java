package com.restaurant.reservations.model;

// Se persiste como ORDINAL (smallint): no reordenar ni insertar valores en medio.
public enum OrderStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED
}
