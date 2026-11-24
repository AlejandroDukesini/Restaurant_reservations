package com.restaurant.reservations.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.restaurant.reservations.dto.ThemeSettings;
import com.restaurant.reservations.model.Restaurant;
import com.restaurant.reservations.model.Role;
import com.restaurant.reservations.model.StaffGroup;
import com.restaurant.reservations.model.User;
import com.restaurant.reservations.repository.UserRepository;
import com.restaurant.reservations.support.IntegrationTest;
import com.restaurant.reservations.support.TestData;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

/** Grupos, mensajeria, personalizacion, imagenes y pruebas de escalada de privilegios. */
class GroupsChatBrandingSecurityIntegrationTest extends IntegrationTest {

    @Autowired private UserRepository userRepository;

    private Restaurant restaurant;
    private User manager;
    private User waiter;
    private User cook;

    @BeforeEach
    void setUp() {
        restaurant = data.restaurant();
        manager = data.user(Role.ADMIN, restaurant);
        waiter = data.user(Role.EMPLOYEE, restaurant);
        cook = data.user(Role.COOK, restaurant);
    }

    private long createGroup(String name) throws Exception {
        return body(mockMvc.perform(post("/api/admin/groups").with(authAs(manager)).with(json(fields("name", name))))
            .andExpect(status().isOk()).andReturn()).get("id").asLong();
    }

    // ------------------------------------------------------------------ Grupos

    @Test
    @DisplayName("el gerente crea, edita, asigna integrantes y archiva grupos; nadie mas puede")
    void administracionDeGrupos() throws Exception {
        long id = createGroup("Salon");
        mockMvc.perform(post("/api/admin/groups").with(authAs(manager)).with(json(fields("name", "salon"))))
            .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/admin/groups").with(authAs(waiter)).with(json(fields("name", "Mio"))))
            .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/admin/groups/" + id + "/members").with(authAs(manager))
                .with(json(fields("userIds", List.of(waiter.getId(), cook.getId())))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.members.length()").value(2));
        mockMvc.perform(put("/api/admin/groups/" + id).with(authAs(manager)).with(json(fields("name", "Salon principal", "chatEnabled", true))))
            .andExpect(jsonPath("$.name").value("Salon principal"));

        // El empleado ve sus grupos; un empleado fuera del grupo no lo ve.
        User outsider = data.user(Role.EMPLOYEE, restaurant);
        mockMvc.perform(get("/api/groups").with(authAs(waiter))).andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(get("/api/groups/" + id).with(authAs(outsider))).andExpect(status().isNotFound());

        // No se agregan usuarios de otro restaurante ni inactivos.
        User foreign = data.user(Role.EMPLOYEE, data.restaurant());
        mockMvc.perform(put("/api/admin/groups/" + id + "/members").with(authAs(manager))
                .with(json(fields("userIds", List.of(foreign.getId())))))
            .andExpect(status().isNotFound());
        User inactive = data.user(Role.EMPLOYEE, restaurant, "baja@example.test", false);
        mockMvc.perform(put("/api/admin/groups/" + id + "/members").with(authAs(manager))
                .with(json(fields("userIds", List.of(inactive.getId())))))
            .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/admin/groups/" + id + "/activity").with(authAs(manager))).andExpect(status().isOk());
        mockMvc.perform(delete("/api/admin/groups/" + id).with(authAs(manager))).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/groups").with(authAs(waiter))).andExpect(jsonPath("$.length()").value(0));
        // Otro restaurante no ve ni toca el grupo.
        User foreignManager = data.user(Role.ADMIN, data.restaurant());
        long other = createGroup("Cocina");
        mockMvc.perform(put("/api/admin/groups/" + other).with(authAs(foreignManager)).with(json(fields("name", "X"))))
            .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ Mensajeria

    @Test
    @DisplayName("solo los integrantes leen y escriben en el grupo; el texto se guarda tal cual")
    void mensajeriaPorGrupo() throws Exception {
        StaffGroup kitchen = data.group(restaurant, "Cocina", waiter, cook);
        String xss = "<img src=x onerror=alert(1)> Mesa 4 sin sal";

        mockMvc.perform(post("/api/chat/groups/" + kitchen.getId() + "/messages").with(authAs(waiter))
                .with(json(fields("body", xss))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.body").value(xss))
            .andExpect(jsonPath("$.mine").value(true));

        mockMvc.perform(get("/api/chat/groups").with(authAs(cook)))
            .andExpect(jsonPath("$[0].unread").value(1));
        mockMvc.perform(get("/api/chat/groups/" + kitchen.getId() + "/messages").with(authAs(cook)))
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].mine").value(false));
        mockMvc.perform(post("/api/chat/groups/" + kitchen.getId() + "/read").with(authAs(cook))).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/chat/groups").with(authAs(cook))).andExpect(jsonPath("$[0].unread").value(0));

        User outsider = data.user(Role.EMPLOYEE, restaurant);
        mockMvc.perform(get("/api/chat/groups/" + kitchen.getId() + "/messages").with(authAs(outsider))).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/chat/groups/" + kitchen.getId() + "/messages").with(authAs(outsider))
            .with(json(fields("body", "hola")))).andExpect(status().isNotFound());
        User foreign = data.user(Role.ADMIN, data.restaurant());
        mockMvc.perform(get("/api/chat/groups/" + kitchen.getId() + "/messages").with(authAs(foreign))).andExpect(status().isNotFound());
        // El gerente administra la comunicacion: puede leer.
        mockMvc.perform(get("/api/chat/groups/" + kitchen.getId() + "/messages").with(authAs(manager))).andExpect(status().isOk());

        // Referencias operativas solo a recursos del propio restaurante.
        long foreignTable = data.table(data.restaurant()).getId();
        mockMvc.perform(post("/api/chat/groups/" + kitchen.getId() + "/messages").with(authAs(waiter))
            .with(json(fields("body", "ver mesa", "refType", "TABLE", "refId", foreignTable)))).andExpect(status().isNotFound());
        long ownTable = data.table(restaurant).getId();
        mockMvc.perform(post("/api/chat/groups/" + kitchen.getId() + "/messages").with(authAs(waiter))
            .with(json(fields("body", "Mesa requiere atencion", "refType", "TABLE", "refId", ownTable)))).andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ Personalizacion

    private static ThemeSettings light() {
        return new ThemeSettings("light", "#1d4ed8", "#0f766e", "#7c3aed", "#ffffff", "#f4f4f5", "#ffffff",
            "#18181b", "#52525b", "#d4d4d8", "#15803d", "#b45309", "#b91c1c", "#1d4ed8", "#ffffff", "#ffffff",
            48, "center", "custom");
    }

    @Test
    @DisplayName("tema por grupo: lo reciben sus integrantes y no los de otros grupos")
    void temaPorGrupo() throws Exception {
        StaffGroup north = data.group(restaurant, "Sede Norte", waiter);
        mockMvc.perform(put("/api/admin/groups/" + north.getId() + "/branding").with(authAs(manager))
                .with(json(fields("theme", light()))))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/branding").with(authAs(waiter)))
            .andExpect(jsonPath("$.source").value("group"))
            .andExpect(jsonPath("$.groupName").value("Sede Norte"))
            .andExpect(jsonPath("$.theme.mode").value("light"));
        mockMvc.perform(get("/api/branding").with(authAs(cook)))
            .andExpect(jsonPath("$.source").value("default"))
            .andExpect(jsonPath("$.theme.mode").value("dark"));
        // El empleado no puede previsualizar ni cambiar temas.
        mockMvc.perform(get("/api/branding").param("groupId", north.getId().toString()).with(authAs(cook))).andExpect(status().isForbidden());
        mockMvc.perform(put("/api/admin/branding").with(authAs(waiter))
            .with(json(fields("restaurantName", "X", "theme", light())))).andExpect(status().isForbidden());

        // Restablecer el grupo: vuelve al tema del restaurante.
        mockMvc.perform(put("/api/admin/groups/" + north.getId() + "/branding").with(authAs(manager)).with(json(fields("theme", null))))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/branding").with(authAs(waiter))).andExpect(jsonPath("$.theme.mode").value("dark"));
    }

    @Test
    @DisplayName("el servidor rechaza combinaciones de colores ilegibles y colores mal formados")
    void temaIlegibleRechazado() throws Exception {
        ThemeSettings t = light();
        ThemeSettings unreadable = new ThemeSettings(t.mode(), t.primary(), t.secondary(), t.accent(), "#ffffff", t.surface(),
            t.card(), "#f4f4f5", t.textMuted(), t.border(), t.success(), t.warning(), t.danger(), t.info(), t.navBackground(),
            t.buttonText(), 40, "left", null);
        mockMvc.perform(put("/api/admin/branding").with(authAs(manager))
                .with(json(fields("restaurantName", restaurant.getName(), "theme", unreadable))))
            .andExpect(status().isBadRequest());
        String injected = "{\"restaurantName\":\"" + restaurant.getName() + "\",\"theme\":{\"mode\":\"light\",\"primary\":\"red;}body{display:none\"}}";
        mockMvc.perform(put("/api/admin/branding").with(authAs(manager)).with(json(injected))).andExpect(status().isBadRequest());

        mockMvc.perform(put("/api/admin/branding").with(authAs(manager))
                .with(json(fields("restaurantName", "Casa Nueva", "theme", light()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.restaurantName").value("Casa Nueva"))
            .andExpect(jsonPath("$.theme.primary").value("#1d4ed8"));
    }

    @Test
    @DisplayName("el tema por defecto del sistema cumple el contraste minimo")
    void temaPorDefectoLegible() {
        ThemeSettings.defaults().requireReadable();
        light().requireReadable();
        assertTrue(ThemeSettings.contrast("#000000", "#ffffff") > 20);
    }

    // ------------------------------------------------------------------ Imagenes

    private static byte[] png() throws Exception {
        BufferedImage image = new BufferedImage(8, 6, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    @Test
    @DisplayName("subidas: solo imagenes reales del gerente; se sirven con su tipo y nosniff")
    void subidaDeImagenes() throws Exception {
        MockMultipartFile image = new MockMultipartFile("file", "logo.png", "image/png", png());
        String id = body(mockMvc.perform(multipart("/api/admin/media").file(image).with(authAs(manager)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.contentType").value("image/png"))
            .andExpect(jsonPath("$.width").value(8))
            .andReturn()).get("id").asText();

        mockMvc.perform(get("/api/media/" + id))
            .andExpect(status().isOk())
            .andExpect(content().contentType("image/png"))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"));

        // SVG con script y un HTML disfrazado de PNG: rechazados por su contenido real.
        MockMultipartFile svg = new MockMultipartFile("file", "logo.svg", "image/svg+xml",
            "<svg xmlns='http://www.w3.org/2000/svg'><script>alert(1)</script></svg>".getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/api/admin/media").file(svg).with(authAs(manager))).andExpect(status().isBadRequest());
        MockMultipartFile fake = new MockMultipartFile("file", "foto.png", "image/png",
            "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/api/admin/media").file(fake).with(authAs(manager))).andExpect(status().isBadRequest());
        mockMvc.perform(multipart("/api/admin/media").file(image).with(authAs(waiter))).andExpect(status().isForbidden());

        // Logo asignado y luego borrado: la referencia queda vacia.
        mockMvc.perform(put("/api/admin/branding").with(authAs(manager))
                .with(json(fields("restaurantName", restaurant.getName(), "logoMediaId", id))))
            .andExpect(jsonPath("$.logoUrl").value("/api/media/" + id));
        User foreignManager = data.user(Role.ADMIN, data.restaurant());
        mockMvc.perform(put("/api/admin/branding").with(authAs(foreignManager))
                .with(json(fields("restaurantName", "Ajeno", "logoMediaId", id))))
            .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/admin/media/" + id).with(authAs(manager))).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/admin/branding").with(authAs(manager))).andExpect(jsonPath("$.logoUrl").doesNotExist());
        mockMvc.perform(get("/api/media/" + id)).andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ Escalada de privilegios

    @Test
    @DisplayName("nadie se convierte en gerente ni se concede permisos")
    void sinEscaladaDePrivilegios() throws Exception {
        // El gerente no puede crear otro gerente ni conceder permisos inexistentes.
        mockMvc.perform(post("/api/admin/staff").with(authAs(manager)).with(json(fields(
            "name", "Falso", "email", "falso@example.test", "password", TestData.PASSWORD, "role", "ADMIN"))))
            .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/admin/staff/" + waiter.getId()).with(authAs(manager)).with(json(fields(
            "name", waiter.getName(), "email", waiter.getEmail(), "role", "EMPLOYEE", "permissions", List.of("ADMIN")))))
            .andExpect(status().isBadRequest());
        // Las cuentas de gerente no son "personal": no se editan por esta via (ni la propia).
        mockMvc.perform(put("/api/admin/staff/" + manager.getId()).with(authAs(manager)).with(json(fields(
            "name", "Yo", "email", manager.getEmail(), "role", "EMPLOYEE"))))
            .andExpect(status().isNotFound());
        // Un empleado no puede tocar personal, ni siquiera a si mismo.
        mockMvc.perform(put("/api/admin/staff/" + waiter.getId()).with(authAs(waiter)).with(json(fields(
            "name", "Yo", "email", waiter.getEmail(), "role", "EMPLOYEE", "permissions", List.of("VIEW_METRICS")))))
            .andExpect(status().isForbidden());
        assertEquals(Role.EMPLOYEE, userRepository.findById(waiter.getId()).orElseThrow().getRole());
        assertEquals(null, userRepository.findById(waiter.getId()).orElseThrow().getPermissions());

        // Permisos concedidos por el gerente: se ven en /me y se aplican en el servidor.
        mockMvc.perform(put("/api/admin/staff/" + waiter.getId()).with(authAs(manager)).with(json(fields(
            "name", waiter.getName(), "email", waiter.getEmail(), "role", "EMPLOYEE", "permissions", List.of("MANAGE_TABLES")))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.permissions[0]").value("MANAGE_TABLES"));
        mockMvc.perform(get("/api/auth/me").with(authAs(waiter))).andExpect(jsonPath("$.permissions[0]").value("MANAGE_TABLES"));
        mockMvc.perform(post("/api/tables/manage").with(authAs(waiter)).with(json(fields(
            "tableNumber", 40, "floor", 1, "capacity", 2, "price", 0.0)))).andExpect(status().isOk());
        // Sin el permiso, ni por la ruta nueva ni por la antigua.
        mockMvc.perform(post("/api/tables/manage").with(authAs(cook)).with(json(fields(
            "tableNumber", 41, "floor", 1, "capacity", 2, "price", 0.0)))).andExpect(status().isForbidden());
        User plainWaiter = data.user(Role.EMPLOYEE, restaurant);
        mockMvc.perform(post("/api/employee/tables").with(authAs(plainWaiter)).with(json(fields(
            "tableNumber", 42, "floor", 1, "capacity", 2, "price", 0.0)))).andExpect(status().isForbidden());

        // Nuevo rol de domiciliario creado por el gerente.
        mockMvc.perform(post("/api/admin/staff").with(authAs(manager)).with(json(fields(
            "name", "Reparto", "email", "reparto@example.test", "password", TestData.PASSWORD, "role", "DELIVERY"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.role").value("DELIVERY"));
    }

    @Test
    @DisplayName("no se pueden leer notificaciones ni marcar como leidas las de otro usuario")
    void notificacionesPropias() throws Exception {
        mockMvc.perform(get("/api/notifications").with(authAs(waiter))).andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(post("/api/notifications/read").with(authAs(cook))).andExpect(status().isNoContent());
        User customer = data.user(Role.CUSTOMER, null);
        mockMvc.perform(get("/api/notifications").with(authAs(customer))).andExpect(status().isForbidden());
    }
}
