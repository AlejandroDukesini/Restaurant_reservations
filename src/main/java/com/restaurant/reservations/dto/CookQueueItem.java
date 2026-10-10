package com.restaurant.reservations.dto;

import java.time.LocalDateTime;

// Un plato pendiente en la cola de cocina, con su receta fija para el cocinero.
public record CookQueueItem(
    long orderItemId,
    long orderId,
    long tableId,
    int tableNumber,
    String tableName,
    String employeeName,
    LocalDateTime orderDate,
    String itemName,
    int quantity,
    String status,
    String notes,
    // Receta (desde el MenuItem)
    String category,
    String protein,
    String condiments,
    String ingredients,
    String preparationNotes
) {
}
