package com.restaurant.reservations.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.restaurant.reservations.model.Reservation;
import com.restaurant.reservations.model.Restaurant;
import com.restaurant.reservations.model.RestaurantTable;
import com.restaurant.reservations.model.Role;
import com.restaurant.reservations.model.TableStatus;
import com.restaurant.reservations.model.User;
import com.restaurant.reservations.repository.ReservationRepository;
import com.restaurant.reservations.support.IntegrationTest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Reglas de negocio de reservas: aforo, solapamiento de franjas de 2 h, mapa de
 * disponibilidad y autorizacion a nivel de objeto (IDOR / aislamiento por restaurante).
 */
class ReservationApiIntegrationTest extends IntegrationTest {

    private static final String GUEST_EMAIL = "invitado@example.test";

    @Autowired private ReservationRepository reservationRepository;

    private Restaurant restaurant;
    private RestaurantTable table;
    private Restaurant otherRestaurant;
    private RestaurantTable otherTable;

    // Fecha fija en el futuro: las pruebas no dependen del reloj del dia de ejecucion.
    private final LocalDate day = LocalDate.now().plusDays(30);
    private final LocalDateTime eightPm = LocalDateTime.of(day, LocalTime.of(20, 0));

    @BeforeEach
    void setUp() {
        restaurant = data.restaurant();
        table = data.table(restaurant, 4, data.zone(restaurant));
        otherRestaurant = data.restaurant();
        otherTable = data.table(otherRestaurant, 4, null);
    }

    private ResultActions publicReservation(LocalDateTime at, Long tableId, Long restaurantId, int guests, String email)
        throws Exception {
        return mockMvc.perform(
            post("/api/public/reservations").with(json(fields(
                "restaurantId", restaurantId,
                "tableId", tableId,
                "reservationDate", at.toString(),
                "numberOfGuests", guests,
                "customerName", "Invitado",
                "customerEmail", email
            )))
        );
    }

    private ResultActions publicReservation() throws Exception {
        return publicReservation(eightPm, GUEST_EMAIL);
    }

    private ResultActions publicReservation(LocalDateTime at, String email) throws Exception {
        return publicReservation(at, table.getId(), restaurant.getId(), 2, email);
    }

    private long customerReservation(User customer) throws Exception {
        return body(
            mockMvc.perform(
                    post("/api/customer/reservations").with(authAs(customer)).with(json(fields(
                        "tableId", table.getId(), "reservationDate", eightPm.toString(), "numberOfGuests", 2
                    )))
                )
                .andExpect(status().isOk())
                .andReturn()
        ).get("id").asLong();
    }

    private String availabilityAt(String time) throws Exception {
        return body(
            mockMvc.perform(
                    get("/api/public/restaurants/" + restaurant.getId() + "/table-map")
                        .param("date", day.toString())
                        .param("time", time)
                )
                .andExpect(status().isOk())
                .andReturn()
        ).get("zones").get(0).get("tables").get(0).get("availability").asText();
    }

    // --- Reserva publica -----------------------------------------------------

    @Test
    @DisplayName("reserva publica valida queda confirmada")
    void reservaPublicaValidaQuedaConfirmada() throws Exception {
        publicReservation()
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CONFIRMED"))
            .andExpect(jsonPath("$.confirmed").value(true))
            .andExpect(jsonPath("$.tableId").value(table.getId()));
    }

    @Test
    @DisplayName("reserva que se solapa con otra en la misma mesa responde 409")
    void reservaQueSeSolapaResponde409() throws Exception {
        publicReservation().andExpect(status().isOk());

        // 21:59 cae dentro de la franja 20:00-22:00.
        publicReservation(eightPm.plusMinutes(119), "otro@example.test")
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("Table already reserved for this time slot"));

        // 18:01 termina a las 20:01, tambien se solapa.
        publicReservation(eightPm.minusMinutes(119), "otro@example.test")
            .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("franjas contiguas no se solapan")
    void franjasContiguasNoSeSolapan() throws Exception {
        publicReservation().andExpect(status().isOk());
        publicReservation(eightPm.plusHours(2), "despues@example.test").andExpect(status().isOk());
        publicReservation(eightPm.minusHours(2), "antes@example.test").andExpect(status().isOk());

        assertEquals(3, reservationRepository.count());
    }

    @Test
    @DisplayName("mas invitados que la capacidad de la mesa responde 400")
    void masInvitadosQueLaCapacidadResponde400() throws Exception {
        publicReservation(eightPm, table.getId(), restaurant.getId(), 5, GUEST_EMAIL)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Number of guests exceeds table capacity"));
    }

    @Test
    @DisplayName("mesa en mantenimiento no se puede reservar")
    void mesaEnMantenimientoNoSePuedeReservar() throws Exception {
        RestaurantTable broken = data.table(restaurant, TableStatus.MAINTENANCE);
        publicReservation(eightPm, broken.getId(), restaurant.getId(), 2, GUEST_EMAIL)
            .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("mesa de otro restaurante responde 404")
    void mesaDeOtroRestauranteResponde404() throws Exception {
        publicReservation(eightPm, otherTable.getId(), restaurant.getId(), 2, GUEST_EMAIL)
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("fecha pasada responde 400")
    void fechaPasadaResponde400() throws Exception {
        publicReservation(LocalDateTime.now().minusDays(1), GUEST_EMAIL).andExpect(status().isBadRequest());
    }

    // --- Mapa de mesas -------------------------------------------------------

    @Test
    @DisplayName("el mapa marca la mesa ocupada solo dentro de la franja reservada")
    void elMapaMarcaLaMesaOcupadaSoloDentroDeLaFranja() throws Exception {
        publicReservation().andExpect(status().isOk());

        assertEquals("OCCUPIED", availabilityAt("20:00"));
        assertEquals("OCCUPIED", availabilityAt("21:59"));
        assertEquals("OCCUPIED", availabilityAt("18:30")); // 18:30-20:30 se cruza con 20:00
        assertEquals("AVAILABLE", availabilityAt("22:00"));
        assertEquals("AVAILABLE", availabilityAt("18:00"));
    }

    @Test
    @DisplayName("una reserva cancelada libera la franja")
    void unaReservaCanceladaLiberaLaFranja() throws Exception {
        User customer = data.user(Role.CUSTOMER, null);
        long id = customerReservation(customer);

        mockMvc.perform(put("/api/customer/reservations/" + id + "/cancel").with(authAs(customer)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CANCELLED"));

        publicReservation().andExpect(status().isOk());
    }

    // --- Autorizacion a nivel de objeto --------------------------------------

    @Test
    @DisplayName("un cliente ve sus reservas pero no las de otro cliente")
    void unClienteVeSusReservasPeroNoLasDeOtro() throws Exception {
        User owner = data.user(Role.CUSTOMER, null);
        User intruder = data.user(Role.CUSTOMER, null);
        long id = customerReservation(owner);

        mockMvc.perform(get("/api/customer/reservations/" + id).with(authAs(owner)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.customerId").value(owner.getId()));
        mockMvc.perform(get("/api/customer/reservations").with(authAs(owner)))
            .andExpect(jsonPath("$.length()").value(1));

        // 404 y no 403: no se confirma la existencia de la reserva ajena.
        mockMvc.perform(get("/api/customer/reservations/" + id).with(authAs(intruder)))
            .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/customer/reservations/" + id + "/cancel").with(authAs(intruder)))
            .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/customer/reservations/" + id).with(authAs(intruder)))
            .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/customer/reservations").with(authAs(intruder)))
            .andExpect(jsonPath("$.length()").value(0));

        assertEquals(1, reservationRepository.count());
    }

    @Test
    @DisplayName("personal del mismo restaurante gestiona la reserva y el de otro restaurante no")
    void personalDelMismoRestauranteGestionaLaReserva() throws Exception {
        long id = customerReservation(data.user(Role.CUSTOMER, null));
        User waiter = data.user(Role.EMPLOYEE, restaurant);
        User foreignAdmin = data.user(Role.ADMIN, otherRestaurant);

        mockMvc.perform(put("/api/customer/reservations/" + id + "/confirm").with(authAs(waiter)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CONFIRMED"));

        mockMvc.perform(get("/api/customer/reservations/" + id).with(authAs(foreignAdmin)))
            .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/customer/reservations/" + id).with(authAs(foreignAdmin)))
            .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/customer/reservations/" + id).with(authAs(waiter)))
            .andExpect(status().isNoContent());
        assertEquals(0, reservationRepository.count());
    }

    @Test
    @DisplayName("no se puede mover una reserva a la mesa de otro restaurante")
    void noSePuedeMoverUnaReservaAOtroRestaurante() throws Exception {
        User customer = data.user(Role.CUSTOMER, null);
        long id = customerReservation(customer);

        mockMvc.perform(
                put("/api/customer/reservations/" + id).with(authAs(customer)).with(json(fields(
                    "tableId", otherTable.getId(), "reservationDate", eightPm.toString(), "numberOfGuests", 2
                )))
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Table belongs to a different restaurant"));
    }

    @Test
    @DisplayName("actualizar la propia reserva a otra hora libre funciona")
    void actualizarLaPropiaReservaFunciona() throws Exception {
        User customer = data.user(Role.CUSTOMER, null);
        long id = customerReservation(customer);
        LocalDateTime newTime = eightPm.plusHours(1);

        mockMvc.perform(
                put("/api/customer/reservations/" + id).with(authAs(customer)).with(json(fields(
                    "tableId", table.getId(), "reservationDate", newTime.toString(), "numberOfGuests", 3
                )))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.numberOfGuests").value(3));

        Reservation stored = reservationRepository.findById(id).orElseThrow();
        assertEquals(newTime, stored.getReservationDate());
        assertEquals(newTime.plusHours(2), stored.getReservationEnd());
    }

    @Test
    @DisplayName("reservas por mesa solo para el personal del restaurante dueno")
    void reservasPorMesaSoloParaElPersonalDelRestauranteDueno() throws Exception {
        customerReservation(data.user(Role.CUSTOMER, null));
        User cook = data.user(Role.COOK, restaurant);
        User foreignWaiter = data.user(Role.EMPLOYEE, otherRestaurant);

        mockMvc.perform(get("/api/public/tables/" + table.getId() + "/reservations").with(authAs(cook)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(get("/api/public/tables/" + table.getId() + "/reservations").with(authAs(foreignWaiter)))
            .andExpect(status().isNotFound());
    }
}
