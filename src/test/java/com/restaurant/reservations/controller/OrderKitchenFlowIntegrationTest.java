package com.restaurant.reservations.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.restaurant.reservations.model.MenuItem;
import com.restaurant.reservations.model.OrderStatus;
import com.restaurant.reservations.model.Restaurant;
import com.restaurant.reservations.model.RestaurantTable;
import com.restaurant.reservations.model.Role;
import com.restaurant.reservations.model.User;
import com.restaurant.reservations.repository.OrderRepository;
import com.restaurant.reservations.support.IntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Flujo principal de la aplicacion: el mesero crea un pedido, la cocina lo ve en
 * la cola con la receta y marca cada plato; el estado del pedido se deriva de sus platos.
 */
class OrderKitchenFlowIntegrationTest extends IntegrationTest {

    @Autowired private OrderRepository orderRepository;

    private Restaurant restaurant;
    private RestaurantTable table;
    private User waiter;
    private User cook;
    private MenuItem risotto;
    private MenuItem wine;

    @BeforeEach
    void setUp() {
        restaurant = data.restaurant();
        table = data.table(restaurant);
        waiter = data.user(Role.EMPLOYEE, restaurant);
        cook = data.user(Role.COOK, restaurant);
        risotto = data.menuItem(restaurant, "Risotto", 32.0);
        wine = data.menuItem(restaurant, "Vino", 12.5);
    }

    /** Lineas del pedido como pares (menuItemId, cantidad). */
    private static Map<String, Object> line(Long menuItemId, int quantity) {
        return fields("menuItemId", menuItemId, "quantity", quantity);
    }

    private ResultActions createOrder(User by, Long tableId, List<Map<String, Object>> items) throws Exception {
        return mockMvc.perform(
            post("/api/employee/orders").with(authAs(by)).with(json(fields(
                "tableId", tableId,
                "items", items,
                "notes", "Sin prisa"
            )))
        );
    }

    private ResultActions createOrder(List<Map<String, Object>> items) throws Exception {
        return createOrder(waiter, table.getId(), items);
    }

    private long createDefaultOrder() throws Exception {
        return body(
            createOrder(List.of(line(risotto.getId(), 2), line(wine.getId(), 1)))
                .andExpect(status().isOk())
                .andReturn()
        ).get("id").asLong();
    }

    private long firstQueuedItemId() throws Exception {
        return body(mockMvc.perform(get("/api/cook/queue").with(authAs(cook))).andReturn())
            .get(0).get("orderItemId").asLong();
    }

    @Test
    @DisplayName("el pedido toma nombre y precio del menu y calcula el total en el servidor")
    void elPedidoTomaNombreYPrecioDelMenu() throws Exception {
        createOrder(List.of(line(risotto.getId(), 2), line(wine.getId(), 1)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.totalAmount").value(76.5)) // 32*2 + 12.5
            .andExpect(jsonPath("$.items.length()").value(2))
            .andExpect(jsonPath("$.items[0].itemName").value("Risotto"))
            .andExpect(jsonPath("$.items[0].price").value(32.0))
            .andExpect(jsonPath("$.employeeId").value(waiter.getId()));
    }

    @Test
    @DisplayName("no se pueden pedir platos ni usar mesas de otro restaurante")
    void noSePuedenPedirPlatosNiUsarMesasDeOtroRestaurante() throws Exception {
        Restaurant other = data.restaurant();
        MenuItem foreignDish = data.menuItem(other);
        RestaurantTable foreignTable = data.table(other);

        createOrder(List.of(line(foreignDish.getId(), 1))).andExpect(status().isNotFound());
        createOrder(waiter, foreignTable.getId(), List.of(line(risotto.getId(), 1))).andExpect(status().isNotFound());
        assertEquals(0, orderRepository.count());
    }

    @Test
    @DisplayName("cantidad fuera de rango se rechaza antes de llegar al servicio")
    void cantidadFueraDeRangoSeRechaza() throws Exception {
        createOrder(List.of(line(risotto.getId(), 0))).andExpect(status().isBadRequest());
        createOrder(List.of(line(risotto.getId(), 501))).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("la cocina ve los platos con su receta y el estado del pedido sigue a los platos")
    void laCocinaVeLosPlatosYElEstadoSigueALosPlatos() throws Exception {
        long orderId = createDefaultOrder();

        JsonNode queue = body(
            mockMvc.perform(get("/api/cook/queue").with(authAs(cook)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].protein").value("Pollo"))
                .andExpect(jsonPath("$[0].tableNumber").value(table.getTableNumber()))
                .andReturn()
        );
        List<Long> itemIds = new ArrayList<>();
        queue.forEach(node -> itemIds.add(node.get("orderItemId").asLong()));

        // Un plato en preparacion -> pedido en curso.
        mockMvc.perform(put("/api/cook/order-items/" + itemIds.get(0) + "/status").param("status", "preparing").with(authAs(cook)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PREPARING"));
        assertEquals(OrderStatus.IN_PROGRESS, orderRepository.findById(orderId).orElseThrow().getStatus());

        // Un plato listo sale de la cola, pero el pedido sigue en curso.
        mockMvc.perform(put("/api/cook/order-items/" + itemIds.get(0) + "/status").param("status", "READY").with(authAs(cook)))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/cook/queue").with(authAs(cook))).andExpect(jsonPath("$.length()").value(1));
        assertEquals(OrderStatus.IN_PROGRESS, orderRepository.findById(orderId).orElseThrow().getStatus());

        // Todos listos -> pedido completado y cola vacia.
        mockMvc.perform(put("/api/cook/order-items/" + itemIds.get(1) + "/status").param("status", "READY").with(authAs(cook)))
            .andExpect(status().isOk());
        assertEquals(OrderStatus.COMPLETED, orderRepository.findById(orderId).orElseThrow().getStatus());
        mockMvc.perform(get("/api/cook/queue").with(authAs(cook))).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("un pedido cancelado no se reabre al cambiar sus platos")
    void unPedidoCanceladoNoSeReabre() throws Exception {
        long orderId = createDefaultOrder();
        mockMvc.perform(put("/api/employee/orders/" + orderId + "/status").param("status", "CANCELLED").with(authAs(waiter)))
            .andExpect(status().isOk());

        long itemId = body(mockMvc.perform(get("/api/employee/orders/" + orderId).with(authAs(waiter))).andReturn())
            .get("items").get(0).get("id").asLong();
        mockMvc.perform(put("/api/cook/order-items/" + itemId + "/status").param("status", "PREPARING").with(authAs(cook)))
            .andExpect(status().isOk());

        assertEquals(OrderStatus.CANCELLED, orderRepository.findById(orderId).orElseThrow().getStatus());
    }

    @Test
    @DisplayName("la cocina de otro restaurante no puede tocar los platos")
    void laCocinaDeOtroRestauranteNoPuedeTocarLosPlatos() throws Exception {
        createDefaultOrder();
        long itemId = firstQueuedItemId();
        User foreignCook = data.user(Role.COOK, data.restaurant());

        mockMvc.perform(get("/api/cook/queue").with(authAs(foreignCook))).andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(put("/api/cook/order-items/" + itemId + "/status").param("status", "READY").with(authAs(foreignCook)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Unauthorized access to order item"));
    }

    @Test
    @DisplayName("estado de plato desconocido responde 400")
    void estadoDePlatoDesconocidoResponde400() throws Exception {
        createDefaultOrder();
        long itemId = firstQueuedItemId();

        mockMvc.perform(put("/api/cook/order-items/" + itemId + "/status").param("status", "QUEMADO").with(authAs(cook)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("parametro de estado ausente responde 400 y no 500")
    void parametroDeEstadoAusenteResponde400() throws Exception {
        long orderId = createDefaultOrder();
        mockMvc.perform(put("/api/employee/orders/" + orderId + "/status").with(authAs(waiter)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("id no numerico responde 400 y no 500")
    void idNoNumericoResponde400() throws Exception {
        mockMvc.perform(get("/api/employee/orders/abc").with(authAs(waiter)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("solo el autor o un admin del restaurante modifican el pedido")
    void soloElAutorOUnAdminModificanElPedido() throws Exception {
        long orderId = createDefaultOrder();
        User otherWaiter = data.user(Role.EMPLOYEE, restaurant);
        User admin = data.user(Role.ADMIN, restaurant);
        User foreignAdmin = data.user(Role.ADMIN, data.restaurant());

        mockMvc.perform(put("/api/employee/orders/" + orderId + "/status").param("status", "COMPLETED").with(authAs(otherWaiter)))
            .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/employee/orders/" + orderId).with(authAs(foreignAdmin)))
            .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/employee/orders/" + orderId).with(authAs(foreignAdmin)))
            .andExpect(status().isNotFound());

        mockMvc.perform(put("/api/employee/orders/" + orderId + "/status").param("status", "BOGUS").with(authAs(waiter)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Invalid order status"));

        mockMvc.perform(delete("/api/employee/orders/" + orderId).with(authAs(admin)))
            .andExpect(status().isNoContent());
        assertEquals(0, orderRepository.count());
    }

    @Test
    @DisplayName("pedidos por mesa y mis pedidos devuelven solo lo propio")
    void pedidosPorMesaYMisPedidosDevuelvenSoloLoPropio() throws Exception {
        createDefaultOrder();
        User otherWaiter = data.user(Role.EMPLOYEE, restaurant);

        mockMvc.perform(get("/api/employee/tables/" + table.getId() + "/orders").with(authAs(otherWaiter)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(get("/api/employee/orders").with(authAs(waiter)))
            .andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(get("/api/employee/orders").with(authAs(otherWaiter)))
            .andExpect(jsonPath("$.length()").value(0));
    }
}
