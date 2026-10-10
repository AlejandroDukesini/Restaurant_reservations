package com.restaurant.reservations.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Alta de cliente. El rol NO se acepta desde el cliente: el registro publico
 * siempre crea un CUSTOMER. El personal (EMPLOYEE/COOK) se crea desde
 * /api/admin/staff y los administradores desde /api/admin/restaurants/register.
 */
public record RegisterRequest(
    @NotBlank
    @Email
    @Size(max = 254)
    String email,

    @NotBlank
    @Size(min = 12, max = 128, message = "La contrasena debe tener entre 12 y 128 caracteres")
    String password,

    @NotBlank
    @Size(max = 120)
    String name
) {
}
