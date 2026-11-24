package com.restaurant.reservations.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.restaurant.reservations.model.DeliveryDriver;
import com.restaurant.reservations.model.DeliveryPlatform;
import com.restaurant.reservations.model.MenuItem;
import com.restaurant.reservations.model.Order;
import com.restaurant.reservations.model.PaymentStatus;
import com.restaurant.reservations.model.Restaurant;
import com.restaurant.reservations.model.RestaurantTable;
import com.restaurant.reservations.model.Role;
import com.restaurant.reservations.model.User;
import com.restaurant.reservations.repository.MenuItemRepository;
import com.restaurant.reservations.repository.OrderRepository;
import com.restaurant.reservations.support.IntegrationTest;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Domicilios (plataformas, domiciliarios, empaque, reparto, tiempos) y administracion del menu. */
class DeliveryAndMenuIntegrationTest extends IntegrationTest {

    @Autowired private OrderRepository orderRepository;
    @Autowired private com.restaurant.reservations.repository.OrderItemRepository orderItemRepository;
    @Autowired private MenuItemRepository menuItemRepository;

    private Restaurant restaurant;
    private User manager;
    private User waiter;
    private User cook;
    private User driverAccount;
    private DeliveryDriver driver;
    private DeliveryPlatform rappi;
    private MenuItem dish;

    @BeforeEach
    void setUp() {
        restaurant = data.restaurant();
        manager = data.user(Role.ADMIN, restaurant);
        waiter = data.user(Role.EMPLOYEE, restaurant);
        cook = data.user(Role.COOK, restaurant);
        driverAccount = data.user(Role.DELIVERY, restaurant);
        driver = data.driver(restaurant, driverAccount);
        rappi = data.platform(restaurant, "Rappi");
        dish = data.menuItem(restaurant, "Hamburguesa", 20.0);
    }

    private long createDelivery(User who, Long driverId) throws Exception {
        return body(mockMvc.perform(post("/api/deliveries").with(authAs(who)).with(json(fields(
                "items", List.of(fields("menuItemId", dish.getId(), "quantity", 2)),
                "customerName", "Cliente", "customerPhone", "3000000000", "deliveryAddress", "Calle 1 # 2-3",
                "platformId", rappi.getId(), "driverId", driverId, "externalRef", "RP-77"
            ))))
            .andExpect(status().isOk())
            .andReturn()).get("id").asLong();
    }

    @Test
    @DisplayName("flujo de domicilio con empaque, reparto, cobro y tiempos")
    void flujoDeDomicilio() throws Exception {
        long id = createDelivery(manager, null);
        mockMvc.perform(get("/api/orders/" + id).with(authAs(manager)))
            .andExpect(jsonPath("$.channel").value("DELIVERY"))
            .andExpect(jsonPath("$.tableId").doesNotExist())
            .andExpect(jsonPath("$.platformName").value("Rappi"));

        // No se empaca antes de que cocina termine; no sale sin empacar.
        mockMvc.perform(post("/api/deliveries/" + id + "/packing/start").with(authAs(cook))).andExpect(status().isConflict());
        mockMvc.perform(post("/api/kitchen/orders/" + id + "/ready").with(authAs(cook))).andExpect(status().isOk());
        mockMvc.perform(post("/api/deliveries/" + id + "/dispatch").with(authAs(manager))).andExpect(status().isConflict());

        mockMvc.perform(post("/api/deliveries/" + id + "/packing/start").with(authAs(cook)))
            .andExpect(jsonPath("$.status").value("PACKING"));
        mockMvc.perform(post("/api/deliveries/" + id + "/packing/finish").with(authAs(cook)))
            .andExpect(jsonPath("$.status").value("PACKED"));
        // Empacado sin domiciliario: no puede salir.
        mockMvc.perform(post("/api/deliveries/" + id + "/dispatch").with(authAs(manager))).andExpect(status().isConflict());

        mockMvc.perform(put("/api/deliveries/" + id + "/driver").with(authAs(manager)).with(json(fields("driverId", driver.getId()))))
            .andExpect(jsonPath("$.driverName").value(driver.getName()));
        mockMvc.perform(get("/api/deliveries/mine").with(authAs(driverAccount)))
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].deliveryAddress").value("Calle 1 # 2-3"));

        mockMvc.perform(post("/api/deliveries/" + id + "/dispatch").with(authAs(driverAccount)))
            .andExpect(jsonPath("$.status").value("ON_THE_WAY"));
        mockMvc.perform(post("/api/deliveries/" + id + "/deliver").with(authAs(driverAccount)))
            .andExpect(jsonPath("$.status").value("DELIVERED"));

        Order done = orderRepository.findById(id).orElseThrow();
        assertEquals(PaymentStatus.PAID, done.getPaymentStatus());
        assertNotNull(done.getPackingAt());
        assertNotNull(done.getPackedAt());
        assertNotNull(done.getDriverAssignedAt());
        assertNotNull(done.getDispatchedAt());
        assertNotNull(done.getDeliveredAt());

        mockMvc.perform(get("/api/metrics").with(authAs(manager)))
            .andExpect(jsonPath("$.summary.revenue").value(40.0))
            .andExpect(jsonPath("$.deliveryTimes.packing.count").value(1))
            .andExpect(jsonPath("$.deliveryTimes.waitingDriver.count").value(1))
            .andExpect(jsonPath("$.deliveryTimes.transit.count").value(1))
            .andExpect(jsonPath("$.revenueByPlatform[0].name").value("Rappi"))
            .andExpect(jsonPath("$.driverPerformance[0].name").value(driver.getName()));
    }

    @Test
    @DisplayName("el domiciliario solo ve y mueve sus domicilios")
    void domiciliarioSoloLoSuyo() throws Exception {
        User otherAccount = data.user(Role.DELIVERY, restaurant);
        data.driver(restaurant, otherAccount);
        long id = createDelivery(manager, driver.getId());

        mockMvc.perform(get("/api/deliveries/mine").with(authAs(otherAccount))).andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/orders/" + id).with(authAs(otherAccount))).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/deliveries/" + id + "/deliver").with(authAs(otherAccount))).andExpect(status().isNotFound());
        // Ni el tablero general, ni la cocina, ni el menu, ni pedidos de mesa.
        mockMvc.perform(get("/api/deliveries").with(authAs(driverAccount))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/kitchen/board").with(authAs(driverAccount))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/staff/menu").with(authAs(driverAccount))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/floor/tables").with(authAs(driverAccount))).andExpect(status().isForbidden());
        // Tampoco puede reasignarse domicilios ni empacarlos.
        mockMvc.perform(put("/api/deliveries/" + id + "/driver").with(authAs(driverAccount)).with(json(fields("driverId", driver.getId()))))
            .andExpect(status().isForbidden());
        // El asignado si ve el suyo.
        mockMvc.perform(get("/api/orders/" + id).with(authAs(driverAccount))).andExpect(status().isOk());
    }

    @Test
    @DisplayName("registrar domicilios exige MANAGE_DELIVERIES; configurar plataformas solo el gerente")
    void permisosDeDomicilios() throws Exception {
        mockMvc.perform(post("/api/deliveries").with(authAs(waiter)).with(json(fields(
            "items", List.of(fields("menuItemId", dish.getId(), "quantity", 1))))))
            .andExpect(status().isForbidden());
        data.withPermissions(waiter, "MANAGE_DELIVERIES");
        createDelivery(waiter, null);
        mockMvc.perform(post("/api/admin/delivery/platforms").with(authAs(waiter)).with(json(fields("name", "Nueva"))))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/delivery/platforms").with(authAs(manager)).with(json(fields("name", "Uber Eats"))))
            .andExpect(status().isOk());
        mockMvc.perform(post("/api/admin/delivery/platforms").with(authAs(manager)).with(json(fields("name", "uber eats"))))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("no se asignan domiciliarios inactivos ni se vinculan cuentas que no son de reparto")
    void validacionDeDomiciliarios() throws Exception {
        long id = createDelivery(manager, null);
        mockMvc.perform(put("/api/admin/delivery/drivers/" + driver.getId()).with(authAs(manager))
                .with(json(fields("name", driver.getName(), "userId", driverAccount.getId(), "active", false))))
            .andExpect(status().isOk());
        mockMvc.perform(put("/api/deliveries/" + id + "/driver").with(authAs(manager)).with(json(fields("driverId", driver.getId()))))
            .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/admin/delivery/drivers").with(authAs(manager))
                .with(json(fields("name", "Falso", "userId", waiter.getId()))))
            .andExpect(status().isBadRequest());
        User foreignDelivery = data.user(Role.DELIVERY, data.restaurant());
        mockMvc.perform(post("/api/admin/delivery/drivers").with(authAs(manager))
                .with(json(fields("name", "Ajeno", "userId", foreignDelivery.getId()))))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("categorias libres, agotados, precios solo del gerente y archivo de platos con historial")
    void administracionDelMenu() throws Exception {
        long categoryId = body(mockMvc.perform(post("/api/admin/menu/categories").with(authAs(manager))
                .with(json(fields("name", "Tortas", "sortOrder", 3))))
            .andExpect(status().isOk()).andReturn()).get("id").asLong();
        mockMvc.perform(post("/api/admin/menu/categories").with(authAs(manager)).with(json(fields("name", "tortas"))))
            .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/admin/menu/categories").with(authAs(waiter)).with(json(fields("name", "X"))))
            .andExpect(status().isForbidden());

        long cake = body(mockMvc.perform(post("/api/admin/menu").with(authAs(manager)).with(json(fields(
                "name", "Torta de chocolate", "categoryId", categoryId, "price", 9.5, "prepMinutes", 5))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.category").value("Tortas"))
            .andExpect(jsonPath("$.prepMinutes").value(5))
            .andReturn()).get("id").asLong();

        // Un mesero no cambia precios ni edita el menu.
        mockMvc.perform(put("/api/admin/menu/" + cake).with(authAs(waiter)).with(json(fields("name", "X", "price", 0.0))))
            .andExpect(status().isForbidden());
        // Agotado: la cocina necesita el permiso; con el, solo cambia la disponibilidad.
        mockMvc.perform(put("/api/menu/items/" + cake + "/availability").with(authAs(cook)).with(json(fields("available", false))))
            .andExpect(status().isForbidden());
        data.withPermissions(cook, "TOGGLE_AVAILABILITY");
        mockMvc.perform(put("/api/menu/items/" + cake + "/availability").with(authAs(cook)).with(json(fields("available", false))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.available").value(false))
            .andExpect(jsonPath("$.price").value(9.5));

        RestaurantTable table = data.table(restaurant);
        mockMvc.perform(post("/api/orders").with(authAs(waiter))
                .with(json(fields("tableId", table.getId(), "items", List.of(fields("menuItemId", cake, "quantity", 1))))))
            .andExpect(status().isConflict());

        // No se borra una categoria con productos activos.
        mockMvc.perform(delete("/api/admin/menu/categories/" + categoryId).with(authAs(manager))).andExpect(status().isConflict());

        // Plato con pedidos: eliminar = archivar (conserva historico).
        mockMvc.perform(post("/api/orders").with(authAs(waiter))
                .with(json(fields("tableId", table.getId(), "items", List.of(fields("menuItemId", dish.getId(), "quantity", 1))))))
            .andExpect(status().isOk());
        mockMvc.perform(delete("/api/admin/menu/" + dish.getId()).with(authAs(manager))).andExpect(status().isNoContent());
        assertFalse(menuItemRepository.findById(dish.getId()).orElseThrow().isActive());
        // Renombrar el plato despues no cambia el historico del pedido.
        assertEquals("Hamburguesa", orderItemRepository.findAll().get(0).getItemName());
    }
}
