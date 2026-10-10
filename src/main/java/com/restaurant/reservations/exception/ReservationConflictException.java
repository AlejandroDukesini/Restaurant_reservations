package com.restaurant.reservations.exception;

public class ReservationConflictException extends BusinessException {
    public ReservationConflictException(String message) {
        super(message);
    }
}
