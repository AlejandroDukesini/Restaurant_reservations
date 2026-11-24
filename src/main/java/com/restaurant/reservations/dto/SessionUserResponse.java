package com.restaurant.reservations.dto;

import java.util.List;

/**
 * Usuario de la sesion actual, con el rol y permisos vigentes en base de datos. El SPA los
 * usa solo para decidir que mostrar; cada operacion se vuelve a autorizar en el servidor.
 */
public record SessionUserResponse(
    long userId,
    String email,
    String role,
    Long restaurantId,
    String name,
    List<String> permissions,
    String avatarUrl
) {
}
