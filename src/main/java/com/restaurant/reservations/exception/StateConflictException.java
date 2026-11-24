package com.restaurant.reservations.exception;

/** La operacion choca con el estado actual del recurso (p. ej. pedido ya entregado): HTTP 409. */
public class StateConflictException extends BusinessException {
    public StateConflictException(String message) {
        super(message);
    }
}
