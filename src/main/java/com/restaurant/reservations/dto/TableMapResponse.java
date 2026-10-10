package com.restaurant.reservations.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record TableMapResponse(
    long restaurantId,
    LocalDate date,
    LocalTime time,
    List<TableMapZoneResponse> zones
) {
}
