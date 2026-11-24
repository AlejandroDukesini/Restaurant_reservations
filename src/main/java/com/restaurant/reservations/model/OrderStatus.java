package com.restaurant.reservations.model;

/**
 * Estado de un pedido (texto en orders.status desde V2).
 *
 * Mesa:      PENDING -> PREPARING -> READY -> DELIVERED
 * Domicilio: PENDING -> PREPARING -> READY -> PACKING -> PACKED -> ON_THE_WAY -> DELIVERED
 * CANCELLED desde cualquier estado anterior a la entrega.
 *
 * "Esperando domiciliario" y "Asignado" no son estados propios: se derivan de PACKED y de
 * si el pedido tiene domiciliario (driver_assigned_at), para no duplicar informacion.
 */
public enum OrderStatus {
    PENDING,
    PREPARING,
    READY,
    PACKING,
    PACKED,
    ON_THE_WAY,
    DELIVERED,
    CANCELLED;

    public boolean isFinal() {
        return this == DELIVERED || this == CANCELLED;
    }

    /** El pedido sigue en manos de cocina. */
    public boolean isInKitchen() {
        return this == PENDING || this == PREPARING;
    }
}
