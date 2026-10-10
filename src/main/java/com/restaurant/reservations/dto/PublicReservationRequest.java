package com.restaurant.reservations.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public record PublicReservationRequest(
    @NotNull
    Long restaurantId,

    @NotNull
    Long tableId,

    @NotNull
    @FutureOrPresent
    LocalDateTime reservationDate,

    // Este endpoint es anonimo: los limites superiores evitan que se use para
    // llenar la base de datos o desvirtuar el aforo.
    @NotNull
    @Min(1)
    @Max(100)
    Integer numberOfGuests,

    @NotBlank
    @Size(max = 120)
    String customerName,

    @NotBlank
    @Email
    @Size(max = 254)
    String customerEmail,

    @Size(max = 1000)
    String specialRequests
) {
}
