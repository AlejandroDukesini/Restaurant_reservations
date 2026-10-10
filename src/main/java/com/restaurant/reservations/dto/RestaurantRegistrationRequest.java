package com.restaurant.reservations.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Alta de restaurante. Crea tambien la cuenta ADMIN del nuevo tenant, por lo que
 * la contrasena del administrador tiene los mismos minimos que el registro publico.
 * Los limites de longitud y de cantidad evitan que un cliente autenticado agote
 * memoria o almacenamiento con valores desproporcionados.
 */
public record RestaurantRegistrationRequest(
    @NotBlank
    @Size(max = 120)
    String name,

    @NotNull
    @Size(max = 2000)
    String description,

    @NotNull
    @Size(max = 300)
    String address,

    @NotNull
    @Size(max = 40)
    String phone,

    @NotNull
    @Email
    @Size(max = 254)
    String email,

    @NotNull
    @Min(0)
    @Max(10_000)
    Integer numberOfTables,

    @NotNull
    @Min(0)
    @Max(100_000)
    Integer numberOfChairs,

    @NotNull
    @Min(1)
    @Max(100)
    Integer numberOfFloors,

    @NotBlank
    @Email
    @Size(max = 254)
    String adminEmail,

    @NotBlank
    @Size(min = 12, max = 128, message = "La contrasena debe tener entre 12 y 128 caracteres")
    String adminPassword,

    @NotBlank
    @Size(max = 120)
    String adminName
) {
}
