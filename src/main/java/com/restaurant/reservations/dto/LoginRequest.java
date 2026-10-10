package com.restaurant.reservations.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
    @NotBlank
    @Size(max = 254)
    String email,

    // Techo de longitud: BCrypt solo usa los primeros 72 bytes y una entrada
    // ilimitada permite gastar CPU del servidor a voluntad (DoS por hashing).
    @NotBlank
    @Size(max = 128)
    String password
) {
}
