package com.restaurant.reservations.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record StaffRequest(
    @NotBlank
    @Size(max = 120)
    String name,

    @Email
    @NotBlank
    @Size(max = 254)
    String email,

    // Requerido al crear; opcional al actualizar (si viene, cambia la contraseña).
    // Minimo alineado con el registro publico: las cuentas de personal acceden a
    // datos de clientes y pedidos, no pueden tener una politica mas debil.
    @Size(min = 12, max = 128, message = "La contrasena debe tener entre 12 y 128 caracteres")
    String password,

    // EMPLOYEE | COOK
    @NotNull
    String role,

    Boolean active
) {
    public StaffRequest {
        if (active == null) {
            active = true;
        }
    }
}
