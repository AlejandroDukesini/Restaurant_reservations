package com.restaurant.reservations.dto;

import java.time.LocalDateTime;
import java.util.List;

public record StaffResponse(
    long id,
    String name,
    String email,
    String role,
    boolean active,
    LocalDateTime createdAt,
    String phone,
    List<String> permissions,
    List<String> groups
) {
}
