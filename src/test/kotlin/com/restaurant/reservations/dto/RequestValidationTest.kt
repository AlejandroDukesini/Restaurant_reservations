package com.restaurant.reservations.dto

import jakarta.validation.Validation
import jakarta.validation.Validator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.LocalDateTime

/**
 * Protege los limites de entrada declarados en los DTO (Bean Validation).
 * Son la primera barrera contra cuentas con claves debiles, DoS por hashing y
 * datos fuera de rango; si se pierde una anotacion, estas pruebas fallan.
 */
class RequestValidationTest {

    private val validator: Validator = Validation.buildDefaultValidatorFactory().validator

    private fun invalidFields(target: Any): Set<String> =
        validator.validate(target).map { it.propertyPath.toString() }.toSet()

    private val future = LocalDateTime.now().plusDays(1)

    // --- Registro -------------------------------------------------------------

    @Test
    fun `registro valido no tiene errores`() {
        assertEquals(emptySet<String>(), invalidFields(RegisterRequest("cliente@example.test", "a".repeat(12), "Cliente")))
    }

    @ParameterizedTest
    @ValueSource(ints = [11, 129])
    fun `registro rechaza contrasenas fuera de 12 a 128 caracteres`(length: Int) {
        assertEquals(setOf("password"), invalidFields(RegisterRequest("cliente@example.test", "a".repeat(length), "Cliente")))
    }

    @Test
    fun `registro acepta los limites exactos de contrasena`() {
        assertEquals(emptySet<String>(), invalidFields(RegisterRequest("c@example.test", "a".repeat(128), "C")))
    }

    @Test
    fun `registro rechaza email mal formado y nombre vacio`() {
        assertEquals(setOf("email", "name"), invalidFields(RegisterRequest("no-es-email", "a".repeat(12), " ")))
    }

    @Test
    fun `login limita la longitud de la contrasena para evitar DoS por hashing`() {
        assertEquals(setOf("password"), invalidFields(LoginRequest("a@example.test", "x".repeat(129))))
        assertEquals(setOf("email", "password"), invalidFields(LoginRequest("", "")))
    }

    // --- Pedidos -----------------------------------------------------------------

    @Test
    fun `pedido sin platos es invalido`() {
        assertEquals(setOf("items"), invalidFields(OrderRequest(tableId = 1, items = emptyList())))
    }

    @ParameterizedTest
    @ValueSource(ints = [0, -1, 501])
    fun `la validacion se propaga a cada plato del pedido`(quantity: Int) {
        val request = OrderRequest(tableId = 1, items = listOf(OrderItemRequest(menuItemId = 1, quantity = quantity)))
        assertEquals(setOf("items[0].quantity"), invalidFields(request))
    }

    @Test
    fun `pedido rechaza mas de 100 lineas`() {
        val items = List(101) { OrderItemRequest(menuItemId = 1, quantity = 1) }
        assertEquals(setOf("items"), invalidFields(OrderRequest(tableId = 1, items = items)))
    }

    // --- Reservas ----------------------------------------------------------------

    @Test
    fun `reserva en el pasado es invalida`() {
        val request = ReservationRequest(tableId = 1, reservationDate = LocalDateTime.now().minusDays(1), numberOfGuests = 2)
        assertEquals(setOf("reservationDate"), invalidFields(request))
    }

    @ParameterizedTest
    @ValueSource(ints = [0, 101])
    fun `reserva rechaza numero de invitados fuera de rango`(guests: Int) {
        assertEquals(setOf("numberOfGuests"), invalidFields(ReservationRequest(1, future, guests)))
    }

    @Test
    fun `reserva publica exige nombre y email valido del cliente`() {
        val request = PublicReservationRequest(
            restaurantId = 1, tableId = 1, reservationDate = future, numberOfGuests = 2,
            customerName = "", customerEmail = "sin-arroba"
        )
        assertEquals(setOf("customerName", "customerEmail"), invalidFields(request))
    }

    // --- Personal y restaurantes ----------------------------------------------

    @Test
    fun `personal sin contrasena es valido (actualizacion sin cambio de clave)`() {
        assertEquals(emptySet<String>(), invalidFields(StaffRequest("Ana", "ana@example.test", null, "COOK")))
    }

    @Test
    fun `personal con contrasena corta es invalido`() {
        assertEquals(setOf("password"), invalidFields(StaffRequest("Ana", "ana@example.test", "corta", "COOK")))
    }

    @Test
    fun `restaurante exige al menos un piso y contrasena fuerte del admin`() {
        val request = RestaurantRegistrationRequest(
            name = "Casa", description = "", address = "", phone = "", email = "casa@example.test",
            numberOfTables = 1, numberOfChairs = 4, numberOfFloors = 0,
            adminEmail = "admin@example.test", adminPassword = "corta", adminName = "Admin"
        )
        assertEquals(setOf("numberOfFloors", "adminPassword"), invalidFields(request))
    }

    @Test
    fun `mesa exige capacidad y coordenadas positivas`() {
        val request = TableRequest(tableNumber = 0, floor = 1, capacity = 0, price = -1.0, gridX = 0)
        assertTrue(invalidFields(request).containsAll(setOf("tableNumber", "capacity", "price", "gridX")))
    }
}
