package com.restaurant.reservations.dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public record ReservationRequest(
    @NotNull
    Long tableId,

    @NotNull
    @FutureOrPresent
    LocalDateTime reservationDate,

    @NotNull
    @Min(1)
    @Max(100)
    Integer numberOfGuests,

    @Size(max = 1000)
    String specialRequests
) {
}
