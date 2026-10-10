package com.restaurant.reservations.dto;

/** Usuario de la sesion actual, con el rol vigente en base de datos. */
public record SessionUserResponse(
    long userId,
    String email,
    String role,
    Long restaurantId
) {
}
