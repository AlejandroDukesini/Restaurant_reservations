package com.restaurant.reservations.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.restaurant.reservations.model.MenuItem;
import com.restaurant.reservations.model.OrderStatus;
import com.restaurant.reservations.model.Restaurant;
import com.restaurant.reservations.model.RestaurantTable;
import com.restaurant.reservations.model.Role;
import com.restaurant.reservations.model.User;
import com.restaurant.reservations.repository.OrderRepository;
import com.restaurant.reservations.support.IntegrationTest;
import java.time.LocalDateTime;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

/**
 * Migraciones de Flyway contra PostgreSQL real: el contexto solo arranca si V1 se aplica
 * y Hibernate valida (ddl-auto=validate) que las entidades coinciden con el esquema creado.
 *
 * H2 no sirve aqui (el SQL es de PostgreSQL), por eso la prueba es opcional: se activa con
 * TEST_POSTGRES_URL (y TEST_POSTGRES_USER / TEST_POSTGRES_PASSWORD). El nombre de la base
 * debe contener "test": la clase base vacia todas las tablas despues de cada prueba.
 */
@EnabledIfEnvironmentVariable(named = "TEST_POSTGRES_URL", matches = "jdbc:postgresql://.+/[^/?]*test[^/?]*(\\?.*)?")
@TestPropertySource(properties = {
    "spring.datasource.url=${TEST_POSTGRES_URL}",
    "spring.datasource.username=${TEST_POSTGRES_USER:postgres}",
    "spring.datasource.password=${TEST_POSTGRES_PASSWORD:postgres}",
    "spring.datasource.driver-class-name=org.postgresql.Driver",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
    "spring.flyway.enabled=true",
    "spring.jpa.hibernate.ddl-auto=validate"
})
class PostgresSchemaIntegrationTest extends IntegrationTest {

    @Autowired private Flyway flyway;
    @Autowired private OrderRepository orderRepository;

    @Test
    @DisplayName("Flyway deja el esquema en la ultima version y Hibernate lo valida")
    void flywayDejaElEsquemaEnLaUltimaVersion() {
        MigrationInfo current = flyway.info().current();
        assertNotNull(current, "sin historial de Flyway");
        assertEquals("1", current.getVersion().getVersion());
        assertEquals(0, flyway.info().pending().length, "quedan migraciones pendientes");
    }

    @Test
    @DisplayName("pedidos y reservas se guardan con enums ordinales, checks y secuencias de PostgreSQL")
    void pedidosYReservasFuncionanSobrePostgres() throws Exception {
        Restaurant restaurant = data.restaurant();
        RestaurantTable table = data.table(restaurant, 4, data.zone(restaurant));
        User waiter = data.user(Role.EMPLOYEE, restaurant);
        User cook = data.user(Role.COOK, restaurant);
        MenuItem dish = data.menuItem(restaurant, "Risotto", 32.0);

        long orderId = body(
            mockMvc.perform(
                    post("/api/employee/orders").with(authAs(waiter)).with(json(fields(
                        "tableId", table.getId(), "items", List.of(fields("menuItemId", dish.getId(), "quantity", 2))
                    )))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAmount").value(64.0))
                .andReturn()
        ).get("id").asLong();

        long itemId = body(mockMvc.perform(get("/api/cook/queue").with(authAs(cook))).andReturn())
            .get(0).get("orderItemId").asLong();
        mockMvc.perform(put("/api/cook/order-items/" + itemId + "/status").param("status", "READY").with(authAs(cook)))
            .andExpect(status().isOk());
        assertEquals(OrderStatus.COMPLETED, orderRepository.findById(orderId).orElseThrow().getStatus());

        LocalDateTime at = LocalDateTime.now().plusDays(5).withHour(20).withMinute(0).withSecond(0).withNano(0);
        mockMvc.perform(
                post("/api/public/reservations").with(json(fields(
                    "restaurantId", restaurant.getId(), "tableId", table.getId(), "reservationDate", at.toString(),
                    "numberOfGuests", 2, "customerName", "Cliente", "customerEmail", "cliente@example.test"
                )))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CONFIRMED"));

        mockMvc.perform(
                get("/api/public/restaurants/" + restaurant.getId() + "/table-map")
                    .param("date", at.toLocalDate().toString())
                    .param("time", "21:00")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.zones[0].tables[0].availability").value("OCCUPIED"));
    }
}
