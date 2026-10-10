package com.restaurant.reservations.dto;

import java.time.LocalDateTime;

public record StaffResponse(
    long id,
    String name,
    String email,
    String role,
    boolean active,
    LocalDateTime createdAt
) {
}
