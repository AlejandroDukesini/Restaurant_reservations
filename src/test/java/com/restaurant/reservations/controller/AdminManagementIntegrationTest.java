package com.restaurant.reservations.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.restaurant.reservations.model.MenuItem;
import com.restaurant.reservations.model.Restaurant;
import com.restaurant.reservations.model.RestaurantTable;
import com.restaurant.reservations.model.Role;
import com.restaurant.reservations.model.User;
import com.restaurant.reservations.model.Zone;
import com.restaurant.reservations.repository.MenuItemRepository;
import com.restaurant.reservations.repository.RestaurantRepository;
import com.restaurant.reservations.repository.TableRepository;
import com.restaurant.reservations.repository.UserRepository;
import com.restaurant.reservations.support.IntegrationTest;
import com.restaurant.reservations.support.TestData;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

/** CRUD del panel de administracion: personal, menu, mesas y restaurantes, con aislamiento por restaurante. */
class AdminManagementIntegrationTest extends IntegrationTest {

    @Autowired private UserRepository userRepository;
    @Autowired private MenuItemRepository menuItemRepository;
    @Autowired private TableRepository tableRepository;
    @Autowired private RestaurantRepository restaurantRepository;

    private Restaurant restaurant;
    private User admin;
    private User foreignAdmin;

    @BeforeEach
    void setUp() {
        restaurant = data.restaurant();
        admin = data.user(Role.ADMIN, restaurant);
        foreignAdmin = data.user(Role.ADMIN, data.restaurant());
    }

    private static Map<String, Object> staffBody(String email, String role, String password) {
        return fields("name", "Chef Prueba", "email", email, "password", password, "role", role, "active", true);
    }

    private static Map<String, Object> staffBody(String email) {
        return staffBody(email, "COOK", TestData.PASSWORD);
    }

    private ResultActions login(String email) throws Exception {
        return mockMvc.perform(post("/api/auth/login").with(json(fields("email", email, "password", TestData.PASSWORD))));
    }

    // --- Personal --------------------------------------------------------------

    @Test
    @DisplayName("alta de personal crea la cuenta en el restaurante del admin y puede iniciar sesion")
    void altaDePersonalCreaLaCuenta() throws Exception {
        mockMvc.perform(post("/api/admin/staff").with(authAs(admin)).with(json(staffBody("chef@example.test"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.role").value("COOK"))
            .andExpect(jsonPath("$.password").doesNotExist());

        User stored = userRepository.findByEmail("chef@example.test").orElseThrow();
        assertEquals(restaurant.getId(), stored.getRestaurant().getId());
        login("chef@example.test").andExpect(status().isOk());
    }

    @Test
    @DisplayName("el email del personal se normaliza para que el login (que normaliza) lo encuentre")
    void elEmailDelPersonalSeNormaliza() throws Exception {
        mockMvc.perform(post("/api/admin/staff").with(authAs(admin)).with(json(staffBody("Chef.Mayus@Example.TEST"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("chef.mayus@example.test"));

        login("Chef.Mayus@Example.TEST").andExpect(status().isOk());
    }

    @Test
    @DisplayName("no se puede crear personal con rol ADMIN ni CUSTOMER")
    void noSePuedeCrearPersonalConRolAdminNiCustomer() throws Exception {
        for (String role : List.of("ADMIN", "CUSTOMER", "INVENTADO")) {
            mockMvc.perform(
                    post("/api/admin/staff").with(authAs(admin))
                        .with(json(staffBody("x-" + role + "@example.test", role, TestData.PASSWORD)))
                )
                .andExpect(status().isBadRequest());
        }
        assertEquals(2, userRepository.count()); // solo los dos admins del setUp
    }

    @Test
    @DisplayName("alta de personal sin contrasena o con email repetido responde 400")
    void altaDePersonalSinContrasenaOConEmailRepetido() throws Exception {
        mockMvc.perform(post("/api/admin/staff").with(authAs(admin)).with(json(staffBody("sin-clave@example.test", "COOK", null))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Password is required"));
        mockMvc.perform(post("/api/admin/staff").with(authAs(admin)).with(json(staffBody(admin.getEmail().toUpperCase(Locale.ROOT)))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Email already registered"));
    }

    @Test
    @DisplayName("listado de personal solo muestra el del propio restaurante")
    void listadoDePersonalSoloMuestraElPropio() throws Exception {
        data.user(Role.EMPLOYEE, restaurant);
        data.user(Role.COOK, foreignAdmin.getRestaurant());

        mockMvc.perform(get("/api/admin/staff").with(authAs(admin)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].role").value("EMPLOYEE"));
    }

    @Test
    @DisplayName("editar sin contrasena conserva la anterior y dar de baja impide el login")
    void editarSinContrasenaConservaLaAnterior() throws Exception {
        User waiter = data.user(Role.EMPLOYEE, restaurant);

        mockMvc.perform(put("/api/admin/staff/" + waiter.getId()).with(authAs(admin)).with(json(staffBody(waiter.getEmail(), "COOK", null))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.role").value("COOK"));
        login(waiter.getEmail()).andExpect(status().isOk());

        mockMvc.perform(delete("/api/admin/staff/" + waiter.getId()).with(authAs(admin))).andExpect(status().isNoContent());
        assertFalse(userRepository.findById(waiter.getId()).orElseThrow().isActive());
        login(waiter.getEmail()).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("editar personal con el email de otra cuenta responde 400 y no 500")
    void editarPersonalConEmailDeOtraCuentaResponde400() throws Exception {
        User waiter = data.user(Role.EMPLOYEE, restaurant);

        mockMvc.perform(put("/api/admin/staff/" + waiter.getId()).with(authAs(admin)).with(json(staffBody(admin.getEmail(), "EMPLOYEE", null))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Email already registered"));
        assertEquals(waiter.getEmail(), userRepository.findById(waiter.getId()).orElseThrow().getEmail());
    }

    @Test
    @DisplayName("un admin no puede editar ni borrar personal de otro restaurante")
    void unAdminNoPuedeEditarNiBorrarPersonalAjeno() throws Exception {
        User foreignWaiter = data.user(Role.EMPLOYEE, foreignAdmin.getRestaurant());

        mockMvc.perform(
                put("/api/admin/staff/" + foreignWaiter.getId()).with(authAs(admin))
                    .with(json(staffBody(foreignWaiter.getEmail(), "EMPLOYEE", TestData.PASSWORD)))
            )
            .andExpect(status().isBadRequest());
        mockMvc.perform(delete("/api/admin/staff/" + foreignWaiter.getId()).with(authAs(admin)))
            .andExpect(status().isBadRequest());
        assertTrue(userRepository.findById(foreignWaiter.getId()).orElseThrow().isActive());
    }

    // --- Menu ------------------------------------------------------------------

    @Test
    @DisplayName("CRUD de menu con baja logica")
    void crudDeMenuConBajaLogica() throws Exception {
        long id = body(
            mockMvc.perform(
                    post("/api/admin/menu").with(authAs(admin)).with(json(fields(
                        "name", "Tiramisu", "category", "dessert", "price", 14.0, "protein", "Ninguna"
                    )))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("DESSERT"))
                .andReturn()
        ).get("id").asLong();

        mockMvc.perform(
                put("/api/admin/menu/" + id).with(authAs(admin))
                    .with(json(fields("name", "Tiramisu Casero", "category", "DESSERT", "price", 15.5)))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.price").value(15.5));

        User waiter = data.user(Role.EMPLOYEE, restaurant);
        mockMvc.perform(get("/api/staff/menu").with(authAs(waiter))).andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(delete("/api/admin/menu/" + id).with(authAs(admin))).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/staff/menu").with(authAs(waiter))).andExpect(jsonPath("$.length()").value(0));
        // Baja logica: el registro sigue existiendo para los pedidos historicos.
        assertFalse(menuItemRepository.findById(id).orElseThrow().isActive());
    }

    @Test
    @DisplayName("menu rechaza categoria invalida, precio negativo y edicion entre restaurantes")
    void menuRechazaCategoriaInvalidaPrecioNegativoYEdicionAjena() throws Exception {
        mockMvc.perform(post("/api/admin/menu").with(authAs(admin)).with(json(fields("name", "X", "category", "SNACK", "price", 1.0))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Invalid category: SNACK"));
        mockMvc.perform(post("/api/admin/menu").with(authAs(admin)).with(json(fields("name", "X", "price", -1.0))))
            .andExpect(status().isBadRequest());

        MenuItem dish = data.menuItem(restaurant);
        mockMvc.perform(put("/api/admin/menu/" + dish.getId()).with(authAs(foreignAdmin)).with(json(fields("name", "Hackeado", "price", 0.0))))
            .andExpect(status().isBadRequest());
        assertEquals(dish.getName(), menuItemRepository.findById(dish.getId()).orElseThrow().getName());
    }

    // --- Mesas -------------------------------------------------------------------

    @Test
    @DisplayName("CRUD de mesas con zona propia y baja logica")
    void crudDeMesasConZonaPropiaYBajaLogica() throws Exception {
        Zone zone = data.zone(restaurant);
        long id = body(
            mockMvc.perform(
                    post("/api/employee/tables").with(authAs(admin)).with(json(fields(
                        "tableNumber", 7, "floor", 1, "capacity", 6, "price", 80.0, "zoneId", zone.getId(), "gridX", 2, "gridY", 3
                    )))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.zoneName").value(zone.getName()))
                .andReturn()
        ).get("id").asLong();

        mockMvc.perform(
                put("/api/employee/tables/" + id).with(authAs(admin)).with(json(fields(
                    "tableNumber", 7, "floor", 1, "capacity", 6, "price", 80.0, "gridX", 4, "gridY", 1
                )))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.gridX").value(4))
            .andExpect(jsonPath("$.zoneId").doesNotExist());

        mockMvc.perform(delete("/api/employee/tables/" + id).with(authAs(admin))).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/staff/tables").with(authAs(admin))).andExpect(jsonPath("$.length()").value(0));
        assertFalse(tableRepository.findById(id).orElseThrow().isActive());
    }

    @Test
    @DisplayName("mesas de otro restaurante no se leen ni modifican y no se usan zonas ajenas")
    void mesasDeOtroRestauranteNoSeLeenNiModifican() throws Exception {
        Restaurant foreignRestaurant = foreignAdmin.getRestaurant();
        RestaurantTable foreignTable = data.table(foreignRestaurant);
        Zone foreignZone = data.zone(foreignRestaurant);

        mockMvc.perform(get("/api/employee/tables/" + foreignTable.getId()).with(authAs(admin))).andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/employee/tables/" + foreignTable.getId()).with(authAs(admin))).andExpect(status().isNotFound());
        mockMvc.perform(
                post("/api/employee/tables").with(authAs(admin)).with(json(fields(
                    "tableNumber", 1, "floor", 1, "capacity", 2, "price", 10.0, "zoneId", foreignZone.getId()
                )))
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Zone not found for restaurant"));

        assertTrue(tableRepository.findById(foreignTable.getId()).orElseThrow().isActive());
    }

    // --- Vistas globales del admin (regresion QA-SEC-01) ---------------------

    private long order(User by, Long tableId, Long dishId) throws Exception {
        return body(
            mockMvc.perform(
                    post("/api/employee/orders").with(authAs(by)).with(json(fields(
                        "tableId", tableId, "items", List.of(fields("menuItemId", dishId, "quantity", 1))
                    )))
                )
                .andExpect(status().isOk())
                .andReturn()
        ).get("id").asLong();
    }

    @Test
    @DisplayName("pedidos y reservas del panel admin se limitan al restaurante propio")
    void pedidosYReservasDelPanelAdminSeLimitanAlRestaurantePropio() throws Exception {
        RestaurantTable ownTable = data.table(restaurant);
        User ownWaiter = data.user(Role.EMPLOYEE, restaurant);
        MenuItem ownDish = data.menuItem(restaurant);
        Restaurant foreignRestaurant = foreignAdmin.getRestaurant();
        RestaurantTable foreignTable = data.table(foreignRestaurant);
        User foreignWaiter = data.user(Role.EMPLOYEE, foreignRestaurant);
        MenuItem foreignDish = data.menuItem(foreignRestaurant);

        long ownOrder = order(ownWaiter, ownTable.getId(), ownDish.getId());
        long foreignOrder = order(foreignWaiter, foreignTable.getId(), foreignDish.getId());
        for (Map.Entry<Long, User> entry : Map.of(ownOrder, ownWaiter, foreignOrder, foreignWaiter).entrySet()) {
            mockMvc.perform(put("/api/employee/orders/" + entry.getKey() + "/status").param("status", "IN_PROGRESS").with(authAs(entry.getValue())))
                .andExpect(status().isOk());
        }

        LocalDateTime reservationAt = LocalDateTime.now().plusDays(10).withHour(20).withMinute(0).withSecond(0).withNano(0);
        for (Map.Entry<Restaurant, RestaurantTable> entry : Map.of(restaurant, ownTable, foreignRestaurant, foreignTable).entrySet()) {
            Restaurant r = entry.getKey();
            mockMvc.perform(
                    post("/api/public/reservations").with(json(fields(
                        "restaurantId", r.getId(), "tableId", entry.getValue().getId(), "reservationDate", reservationAt.toString(),
                        "numberOfGuests", 2, "customerName", "Cliente", "customerEmail", "cliente" + r.getId() + "@example.test"
                    )))
                )
                .andExpect(status().isOk());
        }

        mockMvc.perform(get("/api/admin/orders").with(authAs(admin)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value(ownOrder));
        mockMvc.perform(get("/api/admin/orders/active").with(authAs(admin)))
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value(ownOrder));
        mockMvc.perform(get("/api/admin/reservations").with(authAs(admin)))
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].tableId").value(ownTable.getId()));
        // El otro admin tambien ve solo lo suyo.
        mockMvc.perform(get("/api/admin/orders").with(authAs(foreignAdmin)))
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value(foreignOrder));
    }

    // --- Restaurantes ----------------------------------------------------------

    private static Map<String, Object> registration(String name, String adminEmail) {
        return fields(
            "name", name, "description", "Nuevo local", "address", "Calle 2", "phone", "+00 111",
            "email", "local@example.test", "numberOfTables", 5, "numberOfChairs", 20, "numberOfFloors", 2,
            "adminEmail", adminEmail, "adminPassword", TestData.PASSWORD, "adminName", "Admin Nuevo"
        );
    }

    @Test
    @DisplayName("registrar restaurante crea el admin, genera el sitio y devuelve su URL")
    void registrarRestauranteCreaElAdminYGeneraElSitio() throws Exception {
        mockMvc.perform(
                post("/api/admin/restaurants/register").with(authAs(admin))
                    .with(json(registration("Casa Nueva", "Admin.Nuevo@Example.test")))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.slug").value("casa-nueva"))
            .andExpect(jsonPath("$.websiteUrl").value("/casa-nueva"));

        Restaurant created = restaurantRepository.findBySlug("casa-nueva").orElseThrow();
        assertEquals("/casa-nueva", created.getWebsiteUrl());
        assertTrue(Files.exists(Paths.get("build/test-generated-websites/casa-nueva/index.html")));

        User newAdmin = userRepository.findByEmail("admin.nuevo@example.test").orElseThrow();
        assertEquals(Role.ADMIN, newAdmin.getRole());
        assertEquals(created.getId(), newAdmin.getRestaurant().getId());
        login("admin.nuevo@example.test").andExpect(status().isOk());
    }

    @Test
    @DisplayName("registrar restaurante con nombre repetido o sin letras responde 400")
    void registrarRestauranteConNombreRepetidoOSinLetrasResponde400() throws Exception {
        mockMvc.perform(post("/api/admin/restaurants/register").with(authAs(admin)).with(json(registration(restaurant.getName(), "a1@example.test"))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Restaurant name already exists"));
        mockMvc.perform(post("/api/admin/restaurants/register").with(authAs(admin)).with(json(registration("!!!", "a2@example.test"))))
            .andExpect(status().isBadRequest());
        // Email de administrador ya usado: 400 y sin restaurante huerfano.
        mockMvc.perform(
                post("/api/admin/restaurants/register").with(authAs(admin))
                    .with(json(registration("Casa Duplicada", admin.getEmail().toUpperCase(Locale.ROOT))))
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Email already registered"));
        assertFalse(restaurantRepository.findBySlug("casa-duplicada").isPresent());
    }

    @Test
    @DisplayName("un admin solo lee, edita y desactiva su propio restaurante")
    void unAdminSoloGestionaSuPropioRestaurante() throws Exception {
        Long foreignId = foreignAdmin.getRestaurant().getId();
        mockMvc.perform(get("/api/admin/restaurants/" + foreignId).with(authAs(admin))).andExpect(status().isNotFound());
        mockMvc.perform(put("/api/admin/restaurants/" + foreignId).with(authAs(admin)).with(json(registration("Robado", "r@example.test"))))
            .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/admin/restaurants/" + foreignId).with(authAs(admin))).andExpect(status().isNotFound());
        assertTrue(restaurantRepository.findById(foreignId).orElseThrow().isActive());

        mockMvc.perform(
                put("/api/admin/restaurants/" + restaurant.getId()).with(authAs(admin))
                    .with(json(registration(restaurant.getName(), "r@example.test")))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.numberOfFloors").value(2));
        mockMvc.perform(delete("/api/admin/restaurants/" + restaurant.getId()).with(authAs(admin))).andExpect(status().isNoContent());
        assertFalse(restaurantRepository.findById(restaurant.getId()).orElseThrow().isActive());
        mockMvc.perform(get("/api/public/restaurants/" + restaurant.getSlug())).andExpect(status().is4xxClientError());
    }
}
