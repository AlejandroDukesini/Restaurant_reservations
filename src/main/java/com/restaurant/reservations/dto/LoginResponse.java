package com.restaurant.reservations.dto;

public record LoginResponse(
    String token,
    String type,
    long userId,
    String email,
    String role,
    Long restaurantId
) {
    public LoginResponse(String token, long userId, String email, String role, Long restaurantId) {
        this(token, "Bearer", userId, email, role, restaurantId);
    }
}
