package com.restaurant.reservations.dto;

import java.time.LocalDateTime;

public record ReservationResponse(
    long id,
    long tableId,
    long customerId,
    String customerName,
    LocalDateTime reservationDate,
    int numberOfGuests,
    String status,
    LocalDateTime createdAt,
    String specialRequests,
    boolean confirmed
) {
}
