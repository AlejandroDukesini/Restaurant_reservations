package com.restaurant.reservations.controller;

import java.util.List;

public record ApiErrorResponse(
    int status,
    String error,
    String message,
    List<String> details
) {
}
