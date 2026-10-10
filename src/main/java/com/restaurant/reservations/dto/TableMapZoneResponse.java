package com.restaurant.reservations.dto;

import java.util.List;

public record TableMapZoneResponse(
    Long id,
    String name,
    String code,
    List<TableMapTableResponse> tables
) {
}
