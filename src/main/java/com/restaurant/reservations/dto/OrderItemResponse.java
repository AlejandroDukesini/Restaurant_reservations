package com.restaurant.reservations.dto;

public record OrderItemResponse(
    long id,
    Long menuItemId,
    String itemName,
    int quantity,
    double price,
    String status,
    String notes
) {
}
