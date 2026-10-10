package com.restaurant.reservations.dto;

/** Respuesta del registro: nunca incluye el hash de la contrasena. */
public record RegisterResponse(
    long id,
    String email,
    String name,
    String role
) {
}
