package com.restaurant.reservations.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record OrderRequest(
    @NotNull
    Long tableId,

    // @Valid en la lista propaga la validacion a cada elemento; sin el, los
    // limites de OrderItemRequest no se aplicaban.
    @Valid
    @NotEmpty
    @Size(max = 100)
    List<OrderItemRequest> items,

    @Size(max = 1000)
    String notes
) {
}
