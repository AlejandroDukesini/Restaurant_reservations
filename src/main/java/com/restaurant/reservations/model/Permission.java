package com.restaurant.reservations.model;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Permisos adicionales que el gerente puede conceder a un empleado.
 *
 * Es una lista blanca cerrada: ninguna capacidad exclusiva del gerente (personal, grupos,
 * permisos, precios, menu, personalizacion) existe aqui, asi que no se puede conceder
 * por manipulacion del cliente. Se guardan como texto separado por comas en users.permissions
 * y el backend los lee de la base en cada peticion (no viajan en el JWT).
 */
public enum Permission {
    /** Ver dashboards de tiempos e ingresos. */
    VIEW_METRICS,
    /** Crear, editar y desactivar mesas. */
    MANAGE_TABLES,
    /** Marcar productos como agotados / disponibles (no cambia precios ni datos). */
    TOGGLE_AVAILABILITY,
    /** Crear y gestionar domicilios, asignar domiciliario. */
    MANAGE_DELIVERIES,
    /** Cancelar pedidos de otros empleados. */
    CANCEL_ORDERS,
    /** Ver y operar la vista de cocina sin ser cocinero. */
    KITCHEN_ACCESS;

    public static Set<Permission> parse(String csv) {
        if (csv == null || csv.isBlank()) {
            return EnumSet.noneOf(Permission.class);
        }
        Set<Permission> result = EnumSet.noneOf(Permission.class);
        for (String part : csv.split(",")) {
            String name = part.strip().toUpperCase(Locale.ROOT);
            if (name.isEmpty()) {
                continue;
            }
            // Valores desconocidos (p. ej. de una version futura) se ignoran: nunca amplian acceso.
            Arrays.stream(values()).filter(p -> p.name().equals(name)).findFirst().ifPresent(result::add);
        }
        return result;
    }

    public static String format(Set<Permission> permissions) {
        return permissions.stream().map(Enum::name).sorted().collect(Collectors.joining(","));
    }
}
