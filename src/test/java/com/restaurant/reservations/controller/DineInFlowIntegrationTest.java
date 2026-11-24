package com.restaurant.reservations.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.restaurant.reservations.model.MenuItem;
import com.restaurant.reservations.model.Order;
import com.restaurant.reservations.model.OrderStatus;
import com.restaurant.reservations.model.PaymentStatus;
import com.restaurant.reservations.model.Restaurant;
import com.restaurant.reservations.model.RestaurantTable;
import com.restaurant.reservations.model.Role;
import com.restaurant.reservations.model.StaffGroup;
import com.restaurant.reservations.model.User;
import com.restaurant.reservations.repository.NotificationRepository;
import com.restaurant.reservations.repository.OrderEventRepository;
import com.restaurant.reservations.repository.OrderRepository;
import com.restaurant.reservations.repository.TableRepository;
import com.restaurant.reservations.support.IntegrationTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

/**
 * Flujo real de mesa: MESA -> MESERO -> PEDIDO -> COCINA -> LISTO -> MESERO -> MESA -> CIERRE,
 * con tiempos medidos por el servidor, notificaciones dirigidas y metricas derivadas.
 */
class DineInFlowIntegrationTest extends IntegrationTest {

    @Autowired private OrderRepository orderRepository;
    @Autowired private com.restaurant.reservations.repository.OrderItemRepository orderItemRepository;
    @Autowired private OrderEventRepository eventRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private TableRepository tableRepository;

    private Restaurant restaurant;
    private User manager;
    private User waiter;
    private User cook;
    private RestaurantTable table;
    private MenuItem dish;
    private MenuItem drink;

    @BeforeEach
    void setUp() {
        restaurant = data.restaurant();
        manager = data.user(Role.ADMIN, restaurant);
        waiter = data.user(Role.EMPLOYEE, restaurant);
        cook = data.user(Role.COOK, restaurant);
        table = data.table(restaurant);
        dish = data.menuItem(restaurant, "Risotto", 30.0);
        drink = data.menuItem(restaurant, "Limonada", 5.0);
    }

    private long order(User who, RestaurantTable t, String idempotencyKey, Object... lines) throws Exception {
        var request = post("/api/orders").with(authAs(who)).with(json(fields("tableId", t.getId(), "items", List.of(lines))));
        if (idempotencyKey != null) {
            request.header("Idempotency-Key", idempotencyKey);
        }
        return body(mockMvc.perform(request).andExpect(status().isOk()).andReturn()).get("id").asLong();
    }

    private static Map<String, Object> line(MenuItem item, int qty) {
        return fields("menuItemId", item.getId(), "quantity", qty);
    }

    private String liveState(User who, RestaurantTable t) throws Exception {
        JsonNode board = body(mockMvc.perform(get("/api/floor/tables").with(authAs(who))).andExpect(status().isOk()).andReturn());
        for (JsonNode node : board) {
            if (node.get("id").asLong() == t.getId()) {
                return node.get("liveState").asText();
            }
        }
        return null;
    }

    @Test
    @DisplayName("flujo completo: ocupar, pedir, cocinar, avisar, entregar, cerrar y medir")
    void flujoCompletoDeMesa() throws Exception {
        assertEquals("AVAILABLE", liveState(waiter, table));

        mockMvc.perform(post("/api/floor/tables/" + table.getId() + "/session").with(authAs(waiter)).with(json(fields("guests", 3))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("OPEN"))
            .andExpect(jsonPath("$.guests").value(3));
        assertEquals("OCCUPIED", liveState(waiter, table));

        // Doble toque / reintento de red con la misma clave: un solo pedido.
        long orderId = order(waiter, table, "k-123", line(dish, 2), line(drink, 1));
        assertEquals(orderId, order(waiter, table, "k-123", line(dish, 2), line(drink, 1)));
        assertEquals(1, orderRepository.count());
        assertEquals("SENT", liveState(waiter, table));

        Order created = orderRepository.findById(orderId).orElseThrow();
        assertEquals(65.0, created.getTotalAmount());
        assertEquals(10, created.getEstimatedMinutes());

        // Entregar antes de que cocina termine: rechazado.
        mockMvc.perform(post("/api/orders/" + orderId + "/deliver").with(authAs(waiter))).andExpect(status().isConflict());
        // Cerrar con pedidos en curso: rechazado.
        mockMvc.perform(post("/api/floor/tables/" + table.getId() + "/close").with(authAs(waiter))).andExpect(status().isConflict());

        mockMvc.perform(post("/api/kitchen/orders/" + orderId + "/start").with(authAs(cook)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PREPARING"));
        assertEquals("PREPARING", liveState(waiter, table));

        mockMvc.perform(post("/api/kitchen/orders/" + orderId + "/ready").with(authAs(cook)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("READY"));
        assertEquals("READY", liveState(waiter, table));

        // Solo el mesero del pedido recibe el aviso (no la cocina ni otros meseros).
        var notifications = notificationRepository.findByRecipientIdOrderByIdDesc(waiter.getId(), PageRequest.of(0, 10));
        assertEquals(1, notifications.size());
        assertEquals("ORDER_READY", notifications.get(0).getType());
        assertEquals(0, notificationRepository.countByRecipientIdAndReadAtIsNull(cook.getId()));
        mockMvc.perform(get("/api/notifications/unread").with(authAs(waiter))).andExpect(jsonPath("$.unread").value(1));

        mockMvc.perform(post("/api/orders/" + orderId + "/deliver").with(authAs(waiter)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("DELIVERED"));
        assertEquals("DELIVERED", liveState(waiter, table));

        mockMvc.perform(post("/api/floor/tables/" + table.getId() + "/bill").with(authAs(waiter)))
            .andExpect(jsonPath("$.status").value("BILL_REQUESTED"));
        assertEquals("PAYMENT_PENDING", liveState(waiter, table));

        mockMvc.perform(post("/api/floor/tables/" + table.getId() + "/close").with(authAs(waiter)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CLOSED"))
            .andExpect(jsonPath("$.total").value(65.0));
        assertEquals("AVAILABLE", liveState(waiter, table));

        Order closed = orderRepository.findById(orderId).orElseThrow();
        assertEquals(PaymentStatus.PAID, closed.getPaymentStatus());
        assertNotNull(closed.getPreparingAt());
        assertNotNull(closed.getReadyAt());
        assertNotNull(closed.getDeliveredAt());
        assertTrue(!closed.getReadyAt().isBefore(closed.getPreparingAt()));

        // Historial trazable de quien hizo que.
        List<String> events = eventRepository.findByOrderIdOrderByIdAsc(orderId).stream().map(e -> e.getType()).toList();
        assertEquals(List.of("CREATED", "PREPARING", "READY", "DELIVERED", "PAID"), events);

        // Metricas reales: ingreso solo de lo cobrado y tiempos con su muestra.
        mockMvc.perform(get("/api/metrics").param("period", "today").with(authAs(manager)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.summary.revenue").value(65.0))
            .andExpect(jsonPath("$.summary.orders").value(1))
            .andExpect(jsonPath("$.summary.tablesServed").value(1))
            .andExpect(jsonPath("$.dineInTimes.orderToReady.count").value(1))
            .andExpect(jsonPath("$.dineInTimes.readyToDelivered.count").value(1))
            .andExpect(jsonPath("$.dineInTimes.tableStay.count").value(1))
            .andExpect(jsonPath("$.topProducts[0].name").value("Risotto"))
            .andExpect(jsonPath("$.topProducts[0].count").value(2))
            .andExpect(jsonPath("$.revenueByChannel[0].name").value("Mesa"));
    }

    @Test
    @DisplayName("pedidos de dos atenciones de la misma mesa no se mezclan")
    void pedidosDeDosAtencionesNoSeMezclan() throws Exception {
        long first = order(waiter, table, null, line(drink, 1));
        mockMvc.perform(post("/api/kitchen/orders/" + first + "/ready").with(authAs(cook))).andExpect(status().isOk());
        mockMvc.perform(post("/api/orders/" + first + "/deliver").with(authAs(waiter))).andExpect(status().isOk());
        mockMvc.perform(post("/api/floor/tables/" + table.getId() + "/close").with(authAs(waiter))).andExpect(status().isOk());

        long second = order(waiter, table, null, line(dish, 1));
        JsonNode current = body(mockMvc.perform(get("/api/employee/tables/" + table.getId() + "/orders").with(authAs(waiter))).andReturn());
        assertEquals(1, current.size());
        assertEquals(second, current.get(0).get("id").asLong());
        assertTrue(!orderRepository.findById(first).orElseThrow().getSession().getId()
            .equals(orderRepository.findById(second).orElseThrow().getSession().getId()));
    }

    @Test
    @DisplayName("una mesa no se ocupa dos veces")
    void unaMesaNoSeOcupaDosVeces() throws Exception {
        mockMvc.perform(post("/api/floor/tables/" + table.getId() + "/session").with(authAs(waiter))).andExpect(status().isOk());
        mockMvc.perform(post("/api/floor/tables/" + table.getId() + "/session").with(authAs(manager))).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("cancelacion: el autor antes de cocina; despues solo gerente; cancelados no son ingreso")
    void reglasDeCancelacion() throws Exception {
        long a = order(waiter, table, null, line(dish, 1));
        mockMvc.perform(post("/api/orders/" + a + "/cancel").with(authAs(waiter)).with(json(fields("reason", "Cliente cambio"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CANCELLED"));

        long b = order(waiter, table, null, line(drink, 2));
        mockMvc.perform(post("/api/kitchen/orders/" + b + "/start").with(authAs(cook))).andExpect(status().isOk());
        mockMvc.perform(post("/api/orders/" + b + "/cancel").with(authAs(waiter))).andExpect(status().isConflict());
        User otherWaiter = data.user(Role.EMPLOYEE, restaurant);
        mockMvc.perform(post("/api/orders/" + b + "/cancel").with(authAs(otherWaiter))).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/orders/" + b + "/cancel").with(authAs(manager))).andExpect(status().isOk());
        // El mesero autor recibe aviso de la cancelacion autorizada por el gerente.
        assertEquals("ORDER_CANCELLED",
            notificationRepository.findByRecipientIdOrderByIdDesc(waiter.getId(), PageRequest.of(0, 1)).get(0).getType());

        mockMvc.perform(get("/api/metrics").with(authAs(manager)))
            .andExpect(jsonPath("$.summary.revenue").value(0.0))
            .andExpect(jsonPath("$.summary.orders").value(0))
            .andExpect(jsonPath("$.summary.cancelledOrders").value(2));
    }

    @Test
    @DisplayName("la cocina no puede retroceder un plato listo ni el mesero marcar progreso de cocina")
    void progresoSoloHaciaAdelante() throws Exception {
        long id = order(waiter, table, null, line(dish, 1));
        long itemId = orderItemRepository.findByOrderId(id).get(0).getId();
        mockMvc.perform(put("/api/kitchen/items/" + itemId + "/status").param("status", "READY").with(authAs(cook)))
            .andExpect(status().isNoContent());
        mockMvc.perform(put("/api/kitchen/items/" + itemId + "/status").param("status", "PREPARING").with(authAs(cook)))
            .andExpect(status().isConflict());
        assertEquals(OrderStatus.READY, orderRepository.findById(id).orElseThrow().getStatus());

        long other = order(waiter, table, null, line(drink, 1));
        mockMvc.perform(post("/api/kitchen/orders/" + other + "/ready").with(authAs(waiter))).andExpect(status().isForbidden());
        mockMvc.perform(put("/api/employee/orders/" + other + "/status").param("status", "READY").with(authAs(waiter)))
            .andExpect(status().isConflict());
        assertEquals(OrderStatus.PENDING, orderRepository.findById(other).orElseThrow().getStatus());
    }

    @Test
    @DisplayName("un mesero con KITCHEN_ACCESS puede operar la cocina")
    void permisoDeCocinaParaMesero() throws Exception {
        long id = order(waiter, table, null, line(dish, 1));
        data.withPermissions(waiter, "KITCHEN_ACCESS");
        mockMvc.perform(get("/api/kitchen/board").with(authAs(waiter))).andExpect(status().isOk());
        mockMvc.perform(post("/api/kitchen/orders/" + id + "/start").with(authAs(waiter))).andExpect(status().isOk());
    }

    @Test
    @DisplayName("mesas de un grupo solo las operan sus integrantes")
    void mesasDeGrupoSoloSusIntegrantes() throws Exception {
        User terraceWaiter = data.user(Role.EMPLOYEE, restaurant);
        StaffGroup terrace = data.group(restaurant, "Terraza", terraceWaiter);
        table.setGroup(terrace);
        tableRepository.save(table);

        // Fuera del grupo: no la ve en el tablero, ni puede abrirla, pedir o leer sus pedidos.
        JsonNode board = body(mockMvc.perform(get("/api/floor/tables").with(authAs(waiter))).andReturn());
        assertEquals(0, board.size());
        mockMvc.perform(post("/api/floor/tables/" + table.getId() + "/session").with(authAs(waiter))).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/orders").with(authAs(waiter))
                .with(json(fields("tableId", table.getId(), "items", List.of(line(dish, 1))))))
            .andExpect(status().isNotFound());

        long id = order(terraceWaiter, table, null, line(dish, 1));
        mockMvc.perform(get("/api/orders/" + id).with(authAs(waiter))).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/employee/tables/" + table.getId() + "/orders").with(authAs(waiter))).andExpect(status().isNotFound());
        // El gerente ve todo.
        mockMvc.perform(get("/api/orders/" + id).with(authAs(manager))).andExpect(status().isOk());
    }

    @Test
    @DisplayName("no se pueden usar mesas ni pedidos de otro restaurante cambiando el id")
    void sinAccesoCruzadoPorId() throws Exception {
        Restaurant other = data.restaurant();
        User foreignWaiter = data.user(Role.EMPLOYEE, other);
        User foreignCook = data.user(Role.COOK, other);
        long id = order(waiter, table, null, line(dish, 1));

        mockMvc.perform(post("/api/floor/tables/" + table.getId() + "/session").with(authAs(foreignWaiter))).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/orders/" + id).with(authAs(foreignWaiter))).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/orders/" + id + "/cancel").with(authAs(foreignWaiter))).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/kitchen/orders/" + id + "/ready").with(authAs(foreignCook))).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/kitchen/board").with(authAs(foreignCook))).andExpect(jsonPath("$.length()").value(0));
        assertEquals(OrderStatus.PENDING, orderRepository.findById(id).orElseThrow().getStatus());
    }

    @Test
    @DisplayName("las metricas exigen VIEW_METRICS y el periodo personalizado se valida")
    void accesoAMetricas() throws Exception {
        mockMvc.perform(get("/api/metrics").with(authAs(waiter))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/metrics").with(authAs(cook))).andExpect(status().isForbidden());
        data.withPermissions(waiter, "VIEW_METRICS");
        mockMvc.perform(get("/api/metrics").param("period", "week").with(authAs(waiter))).andExpect(status().isOk());
        mockMvc.perform(get("/api/metrics").param("period", "custom").param("from", "2026-02-10").param("to", "2026-02-01")
            .with(authAs(manager))).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/metrics").param("period", "custom").param("from", "2026-01-01").param("to", "2026-01-31")
            .with(authAs(manager))).andExpect(status().isOk());
    }

    @Test
    @DisplayName("el canal en tiempo real acepta personal autenticado y rechaza anonimos")
    void canalEnTiempoReal() throws Exception {
        mockMvc.perform(get("/api/stream").param("device", "dispositivo-prueba-1").with(authAs(waiter)))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/stream")).andExpect(status().isUnauthorized());
    }
}
