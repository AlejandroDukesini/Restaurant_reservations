package com.restaurant.reservations.controller

import com.restaurant.reservations.model.Restaurant
import com.restaurant.reservations.model.RestaurantTable
import com.restaurant.reservations.model.Role
import com.restaurant.reservations.model.TableStatus
import com.restaurant.reservations.model.User
import com.restaurant.reservations.repository.ReservationRepository
import com.restaurant.reservations.support.IntegrationTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Reglas de negocio de reservas: aforo, solapamiento de franjas de 2 h, mapa de
 * disponibilidad y autorizacion a nivel de objeto (IDOR / aislamiento por restaurante).
 */
class ReservationApiIntegrationTest : IntegrationTest() {

    @Autowired private lateinit var reservationRepository: ReservationRepository

    private lateinit var restaurant: Restaurant
    private lateinit var table: RestaurantTable
    private lateinit var otherRestaurant: Restaurant
    private lateinit var otherTable: RestaurantTable

    // Fecha fija en el futuro: las pruebas no dependen del reloj del dia de ejecucion.
    private val day: LocalDate = LocalDate.now().plusDays(30)
    private val eightPm: LocalDateTime = LocalDateTime.of(day, LocalTime.of(20, 0))

    @BeforeEach
    fun setUp() {
        restaurant = data.restaurant()
        table = data.table(restaurant, capacity = 4, zone = data.zone(restaurant))
        otherRestaurant = data.restaurant()
        otherTable = data.table(otherRestaurant, capacity = 4)
    }

    private fun publicReservation(
        at: LocalDateTime = eightPm,
        tableId: Long = table.id!!,
        restaurantId: Long = restaurant.id!!,
        guests: Int = 2,
        email: String = "invitado@example.test"
    ): ResultActions = mockMvc.perform(
        post("/api/public/reservations").json(
            mapOf(
                "restaurantId" to restaurantId,
                "tableId" to tableId,
                "reservationDate" to at.toString(),
                "numberOfGuests" to guests,
                "customerName" to "Invitado",
                "customerEmail" to email
            )
        )
    )

    private fun customerReservation(customer: User, at: LocalDateTime = eightPm, tableId: Long = table.id!!): Long =
        mockMvc.perform(
            post("/api/customer/reservations").authAs(customer).json(
                mapOf("tableId" to tableId, "reservationDate" to at.toString(), "numberOfGuests" to 2)
            )
        ).andExpect(status().isOk).andReturn().body()["id"].asLong()

    // --- Reserva publica -----------------------------------------------------

    @Test
    fun `reserva publica valida queda confirmada`() {
        publicReservation()
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("CONFIRMED"))
            .andExpect(jsonPath("$.confirmed").value(true))
            .andExpect(jsonPath("$.tableId").value(table.id!!))
    }

    @Test
    fun `reserva que se solapa con otra en la misma mesa responde 409`() {
        publicReservation().andExpect(status().isOk)

        // 21:59 cae dentro de la franja 20:00-22:00.
        publicReservation(at = eightPm.plusMinutes(119), email = "otro@example.test")
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.message").value("Table already reserved for this time slot"))

        // 18:01 termina a las 20:01, tambien se solapa.
        publicReservation(at = eightPm.minusMinutes(119), email = "otro@example.test")
            .andExpect(status().isConflict)
    }

    @Test
    fun `franjas contiguas no se solapan`() {
        publicReservation().andExpect(status().isOk)
        publicReservation(at = eightPm.plusHours(2), email = "despues@example.test").andExpect(status().isOk)
        publicReservation(at = eightPm.minusHours(2), email = "antes@example.test").andExpect(status().isOk)

        assertEquals(3, reservationRepository.count())
    }

    @Test
    fun `mas invitados que la capacidad de la mesa responde 400`() {
        publicReservation(guests = 5)
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("Number of guests exceeds table capacity"))
    }

    @Test
    fun `mesa en mantenimiento no se puede reservar`() {
        val broken = data.table(restaurant, status = TableStatus.MAINTENANCE)
        publicReservation(tableId = broken.id!!).andExpect(status().isConflict)
    }

    @Test
    fun `mesa de otro restaurante responde 404`() {
        publicReservation(tableId = otherTable.id!!)
            .andExpect(status().isNotFound)
    }

    @Test
    fun `fecha pasada responde 400`() {
        publicReservation(at = LocalDateTime.now().minusDays(1)).andExpect(status().isBadRequest)
    }

    // --- Mapa de mesas -------------------------------------------------------

    @Test
    fun `el mapa marca la mesa ocupada solo dentro de la franja reservada`() {
        publicReservation().andExpect(status().isOk)

        fun availabilityAt(time: String) = mockMvc.perform(
            get("/api/public/restaurants/${restaurant.id}/table-map").param("date", day.toString()).param("time", time)
        ).andExpect(status().isOk).andReturn().body()["zones"][0]["tables"][0]["availability"].asText()

        assertEquals("OCCUPIED", availabilityAt("20:00"))
        assertEquals("OCCUPIED", availabilityAt("21:59"))
        assertEquals("OCCUPIED", availabilityAt("18:30")) // 18:30-20:30 se cruza con 20:00
        assertEquals("AVAILABLE", availabilityAt("22:00"))
        assertEquals("AVAILABLE", availabilityAt("18:00"))
    }

    @Test
    fun `una reserva cancelada libera la franja`() {
        val customer = data.user(Role.CUSTOMER, null)
        val id = customerReservation(customer)

        mockMvc.perform(put("/api/customer/reservations/$id/cancel").authAs(customer))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("CANCELLED"))

        publicReservation().andExpect(status().isOk)
    }

    // --- Autorizacion a nivel de objeto --------------------------------------

    @Test
    fun `un cliente ve sus reservas pero no las de otro cliente`() {
        val owner = data.user(Role.CUSTOMER, null)
        val intruder = data.user(Role.CUSTOMER, null)
        val id = customerReservation(owner)

        mockMvc.perform(get("/api/customer/reservations/$id").authAs(owner))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.customerId").value(owner.id!!))
        mockMvc.perform(get("/api/customer/reservations").authAs(owner))
            .andExpect(jsonPath("$.length()").value(1))

        // 404 y no 403: no se confirma la existencia de la reserva ajena.
        mockMvc.perform(get("/api/customer/reservations/$id").authAs(intruder)).andExpect(status().isNotFound)
        mockMvc.perform(put("/api/customer/reservations/$id/cancel").authAs(intruder)).andExpect(status().isNotFound)
        mockMvc.perform(delete("/api/customer/reservations/$id").authAs(intruder)).andExpect(status().isNotFound)
        mockMvc.perform(get("/api/customer/reservations").authAs(intruder))
            .andExpect(jsonPath("$.length()").value(0))

        assertEquals(1, reservationRepository.count())
    }

    @Test
    fun `personal del mismo restaurante gestiona la reserva y el de otro restaurante no`() {
        val id = customerReservation(data.user(Role.CUSTOMER, null))
        val waiter = data.user(Role.EMPLOYEE, restaurant)
        val foreignAdmin = data.user(Role.ADMIN, otherRestaurant)

        mockMvc.perform(put("/api/customer/reservations/$id/confirm").authAs(waiter))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("CONFIRMED"))

        mockMvc.perform(get("/api/customer/reservations/$id").authAs(foreignAdmin)).andExpect(status().isNotFound)
        mockMvc.perform(delete("/api/customer/reservations/$id").authAs(foreignAdmin)).andExpect(status().isNotFound)

        mockMvc.perform(delete("/api/customer/reservations/$id").authAs(waiter)).andExpect(status().isNoContent)
        assertEquals(0, reservationRepository.count())
    }

    @Test
    fun `no se puede mover una reserva a la mesa de otro restaurante`() {
        val customer = data.user(Role.CUSTOMER, null)
        val id = customerReservation(customer)

        mockMvc.perform(
            put("/api/customer/reservations/$id").authAs(customer).json(
                mapOf("tableId" to otherTable.id, "reservationDate" to eightPm.toString(), "numberOfGuests" to 2)
            )
        ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("Table belongs to a different restaurant"))
    }

    @Test
    fun `actualizar la propia reserva a otra hora libre funciona`() {
        val customer = data.user(Role.CUSTOMER, null)
        val id = customerReservation(customer)
        val newTime = eightPm.plusHours(1)

        mockMvc.perform(
            put("/api/customer/reservations/$id").authAs(customer).json(
                mapOf("tableId" to table.id, "reservationDate" to newTime.toString(), "numberOfGuests" to 3)
            )
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.numberOfGuests").value(3))

        val stored = reservationRepository.findById(id).orElseThrow()
        assertEquals(newTime, stored.reservationDate)
        assertEquals(newTime.plusHours(2), stored.reservationEnd)
    }

    @Test
    fun `reservas por mesa solo para el personal del restaurante dueno`() {
        customerReservation(data.user(Role.CUSTOMER, null))
        val cook = data.user(Role.COOK, restaurant)
        val foreignWaiter = data.user(Role.EMPLOYEE, otherRestaurant)

        mockMvc.perform(get("/api/public/tables/${table.id}/reservations").authAs(cook))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
        mockMvc.perform(get("/api/public/tables/${table.id}/reservations").authAs(foreignWaiter))
            .andExpect(status().isNotFound)
    }
}
