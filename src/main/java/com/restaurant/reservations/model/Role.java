package com.restaurant.reservations.model;

/**
 * Roles del sistema. Se persisten como texto (users.role).
 * ADMIN = gerente de UN restaurante (no es un superusuario global).
 * EMPLOYEE = mesero. COOK = cocina. DELIVERY = domiciliario con cuenta.
 */
public enum Role {
    ADMIN,
    EMPLOYEE,
    COOK,
    CUSTOMER,
    DELIVERY
}
