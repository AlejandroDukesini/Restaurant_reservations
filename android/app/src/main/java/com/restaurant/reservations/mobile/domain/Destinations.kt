package com.restaurant.reservations.mobile.domain

/** Secciones de la app. Equivalen a las rutas del SPA: /piso, /cocina y la pestana Reservas de /admin. */
enum class Destination(val label: String) {
    FLOOR("Piso"),
    KITCHEN("Cocina"),
    RESERVATIONS("Reservas")
}

/**
 * Secciones visibles para cada rol, con el mismo reparto que el SPA (ProtectedRoute y Navbar).
 * Ocultar una seccion es solo comodidad: el servidor responde 403 aunque se llame al endpoint.
 * CUSTOMER u otro rol no tiene secciones: esta app es para el personal del restaurante.
 */
fun destinationsFor(role: String?): List<Destination> = when (role) {
    "ADMIN" -> listOf(Destination.FLOOR, Destination.KITCHEN, Destination.RESERVATIONS)
    "EMPLOYEE" -> listOf(Destination.FLOOR)
    "COOK" -> listOf(Destination.KITCHEN)
    else -> emptyList()
}
