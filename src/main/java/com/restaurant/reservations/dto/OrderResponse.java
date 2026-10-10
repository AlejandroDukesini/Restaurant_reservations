package com.restaurant.reservations.dto;

import java.time.LocalDateTime;
import java.util.List;

public record OrderResponse(
    long id,
    long tableId,
    int tableNumber,
    long employeeId,
    String employeeName,
    LocalDateTime orderDate,
    String status,
    double totalAmount,
    List<OrderItemResponse> items,
    String notes
) {
}
