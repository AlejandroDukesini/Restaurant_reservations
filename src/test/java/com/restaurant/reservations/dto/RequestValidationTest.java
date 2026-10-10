package com.restaurant.reservations.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Protege los limites de entrada declarados en los DTO (Bean Validation).
 * Son la primera barrera contra cuentas con claves debiles, DoS por hashing y
 * datos fuera de rango; si se pierde una anotacion, estas pruebas fallan.
 */
class RequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    private final LocalDateTime future = LocalDateTime.now().plusDays(1);

    private Set<String> invalidFields(Object target) {
        return validator.validate(target).stream()
            .map(violation -> violation.getPropertyPath().toString())
            .collect(Collectors.toSet());
    }

    // --- Registro -------------------------------------------------------------

    @Test
    @DisplayName("registro valido no tiene errores")
    void registroValidoNoTieneErrores() {
        assertEquals(Set.of(), invalidFields(new RegisterRequest("cliente@example.test", "a".repeat(12), "Cliente")));
    }

    @ParameterizedTest
    @ValueSource(ints = {11, 129})
    @DisplayName("registro rechaza contrasenas fuera de 12 a 128 caracteres")
    void registroRechazaContrasenasFueraDeRango(int length) {
        assertEquals(
            Set.of("password"),
            invalidFields(new RegisterRequest("cliente@example.test", "a".repeat(length), "Cliente"))
        );
    }

    @Test
    @DisplayName("registro acepta los limites exactos de contrasena")
    void registroAceptaLosLimitesExactos() {
        assertEquals(Set.of(), invalidFields(new RegisterRequest("c@example.test", "a".repeat(128), "C")));
    }

    @Test
    @DisplayName("registro rechaza email mal formado y nombre vacio")
    void registroRechazaEmailYNombreInvalidos() {
        assertEquals(Set.of("email", "name"), invalidFields(new RegisterRequest("no-es-email", "a".repeat(12), " ")));
    }

    @Test
    @DisplayName("login limita la longitud de la contrasena para evitar DoS por hashing")
    void loginLimitaLongitudDeContrasena() {
        assertEquals(Set.of("password"), invalidFields(new LoginRequest("a@example.test", "x".repeat(129))));
        assertEquals(Set.of("email", "password"), invalidFields(new LoginRequest("", "")));
    }

    // --- Pedidos -----------------------------------------------------------------

    @Test
    @DisplayName("pedido sin platos es invalido")
    void pedidoSinPlatosEsInvalido() {
        assertEquals(Set.of("items"), invalidFields(new OrderRequest(1L, List.of(), null)));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 501})
    @DisplayName("la validacion se propaga a cada plato del pedido")
    void laValidacionSePropagaACadaPlato(int quantity) {
        OrderRequest request = new OrderRequest(1L, List.of(new OrderItemRequest(1L, quantity, null)), null);
        assertEquals(Set.of("items[0].quantity"), invalidFields(request));
    }

    @Test
    @DisplayName("pedido rechaza mas de 100 lineas")
    void pedidoRechazaMasDe100Lineas() {
        List<OrderItemRequest> items = Collections.nCopies(101, new OrderItemRequest(1L, 1, null));
        assertEquals(Set.of("items"), invalidFields(new OrderRequest(1L, items, null)));
    }

    // --- Reservas ----------------------------------------------------------------

    @Test
    @DisplayName("reserva en el pasado es invalida")
    void reservaEnElPasadoEsInvalida() {
        ReservationRequest request = new ReservationRequest(1L, LocalDateTime.now().minusDays(1), 2, null);
        assertEquals(Set.of("reservationDate"), invalidFields(request));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 101})
    @DisplayName("reserva rechaza numero de invitados fuera de rango")
    void reservaRechazaInvitadosFueraDeRango(int guests) {
        assertEquals(Set.of("numberOfGuests"), invalidFields(new ReservationRequest(1L, future, guests, null)));
    }

    @Test
    @DisplayName("reserva publica exige nombre y email valido del cliente")
    void reservaPublicaExigeNombreYEmail() {
        PublicReservationRequest request = new PublicReservationRequest(
            1L, 1L, future, 2, "", "sin-arroba", null
        );
        assertEquals(Set.of("customerName", "customerEmail"), invalidFields(request));
    }

    // --- Personal y restaurantes ----------------------------------------------

    @Test
    @DisplayName("personal sin contrasena es valido (actualizacion sin cambio de clave)")
    void personalSinContrasenaEsValido() {
        assertEquals(Set.of(), invalidFields(new StaffRequest("Ana", "ana@example.test", null, "COOK", null)));
    }

    @Test
    @DisplayName("personal con contrasena corta es invalido")
    void personalConContrasenaCortaEsInvalido() {
        assertEquals(Set.of("password"), invalidFields(new StaffRequest("Ana", "ana@example.test", "corta", "COOK", null)));
    }

    @Test
    @DisplayName("restaurante exige al menos un piso y contrasena fuerte del admin")
    void restauranteExigePisoYContrasenaFuerte() {
        RestaurantRegistrationRequest request = new RestaurantRegistrationRequest(
            "Casa", "", "", "", "casa@example.test",
            1, 4, 0,
            "admin@example.test", "corta", "Admin"
        );
        assertEquals(Set.of("numberOfFloors", "adminPassword"), invalidFields(request));
    }

    @Test
    @DisplayName("mesa exige capacidad y coordenadas positivas")
    void mesaExigeCapacidadYCoordenadasPositivas() {
        TableRequest request = new TableRequest(0, null, 1, 0, -1.0, null, 0, null);
        assertTrue(invalidFields(request).containsAll(Set.of("tableNumber", "capacity", "price", "gridX")));
    }
}
