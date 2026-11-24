package com.restaurant.reservations.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.restaurant.reservations.dto.ThemeSettings;
import com.restaurant.reservations.model.Restaurant;
import com.restaurant.reservations.model.Role;
import com.restaurant.reservations.model.StaffGroup;
import com.restaurant.reservations.model.User;
import com.restaurant.reservations.repository.UserRepository;
import com.restaurant.reservations.support.IntegrationTest;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

/** Perfil propio: nombre, foto y tema personal de cada usuario (gerente incluido). */
class ProfileIntegrationTest extends IntegrationTest {

    @Autowired private UserRepository userRepository;

    private Restaurant restaurant;
    private User manager;
    private User waiter;

    @BeforeEach
    void setUp() {
        restaurant = data.restaurant();
        manager = data.user(Role.ADMIN, restaurant);
        waiter = data.user(Role.EMPLOYEE, restaurant);
    }

    private static ThemeSettings ocean() {
        return new ThemeSettings("dark", "#38bdf8", "#0369a1", "#bae6fd", "#06111f", "#0c1b2e", "#0a1626", "#e2e8f0",
            "#94a3b8", "#1e3a5f", "#4ade80", "#fbbf24", "#fb7185", "#7dd3fc", "#06111f", "#04111d", 40, "left", "oceano");
    }

    @Test
    @DisplayName("cada usuario cambia su nombre y su tema personal, que tiene prioridad sobre el del grupo")
    void nombreYTemaPersonal() throws Exception {
        StaffGroup group = data.group(restaurant, "Sala", waiter);
        group.setTheme("{\"mode\":\"light\"}");
        mockMvc.perform(put("/api/me/profile").with(authAs(waiter)).with(json(fields("name", "Carla", "theme", ocean()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Carla"))
            .andExpect(jsonPath("$.theme.preset").value("oceano"));
        mockMvc.perform(get("/api/branding").with(authAs(waiter)))
            .andExpect(jsonPath("$.source").value("personal"))
            .andExpect(jsonPath("$.theme.primary").value("#38bdf8"));
        mockMvc.perform(get("/api/auth/me").with(authAs(waiter))).andExpect(jsonPath("$.name").value("Carla"));

        // Volver a heredar.
        mockMvc.perform(put("/api/me/profile").with(authAs(waiter)).with(json(fields("name", "Carla", "theme", null))))
            .andExpect(jsonPath("$.theme").doesNotExist());
        mockMvc.perform(get("/api/branding").with(authAs(waiter))).andExpect(jsonPath("$.source").value("default"));

        // El gerente tambien personaliza su nombre.
        mockMvc.perform(put("/api/me/profile").with(authAs(manager)).with(json(fields("name", "Gerente Ana"))))
            .andExpect(jsonPath("$.name").value("Gerente Ana"))
            .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    @DisplayName("el perfil no permite cambiar rol, correo ni temas ilegibles")
    void sinEscaladaDesdeElPerfil() throws Exception {
        mockMvc.perform(put("/api/me/profile").with(authAs(waiter))
                .with(json("{\"name\":\"Yo\",\"role\":\"ADMIN\",\"email\":\"otro@example.test\",\"permissions\":[\"VIEW_METRICS\"]}")))
            .andExpect(status().isOk());
        User reloaded = userRepository.findById(waiter.getId()).orElseThrow();
        assertEquals(Role.EMPLOYEE, reloaded.getRole());
        assertEquals(waiter.getEmail(), reloaded.getEmail());
        assertNull(reloaded.getPermissions());

        ThemeSettings t = ocean();
        ThemeSettings unreadable = new ThemeSettings("dark", t.primary(), t.secondary(), t.accent(), t.background(),
            t.surface(), t.card(), "#0a1626", t.textMuted(), t.border(), t.success(), t.warning(), t.danger(), t.info(),
            t.navBackground(), t.buttonText(), 40, "left", null);
        mockMvc.perform(put("/api/me/profile").with(authAs(waiter)).with(json(fields("name", "Yo", "theme", unreadable))))
            .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/me/profile").with(authAs(waiter)).with(json(fields("name", " "))))
            .andExpect(status().isBadRequest());
        User customer = data.user(Role.CUSTOMER, null);
        mockMvc.perform(get("/api/me/profile").with(authAs(customer))).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("foto de perfil: imagen real, se reemplaza y se quita")
    void fotoDePerfil() throws Exception {
        BufferedImage image = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        MockMultipartFile png = new MockMultipartFile("file", "yo.png", "image/png", out.toByteArray());

        String first = body(mockMvc.perform(multipart("/api/me/avatar").file(png).with(authAs(waiter)))
            .andExpect(status().isOk()).andReturn()).get("avatarUrl").asText();
        String second = body(mockMvc.perform(multipart("/api/me/avatar").file(png).with(authAs(waiter)))
            .andExpect(status().isOk()).andReturn()).get("avatarUrl").asText();
        // La foto anterior se borra al reemplazarla.
        mockMvc.perform(get(first)).andExpect(status().isNotFound());
        mockMvc.perform(get(second)).andExpect(status().isOk());
        mockMvc.perform(get("/api/auth/me").with(authAs(waiter))).andExpect(jsonPath("$.avatarUrl").value(second));

        MockMultipartFile fake = new MockMultipartFile("file", "x.png", "image/png",
            "<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/api/me/avatar").file(fake).with(authAs(waiter))).andExpect(status().isBadRequest());

        mockMvc.perform(delete("/api/me/avatar").with(authAs(waiter)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.avatarUrl").doesNotExist());
        mockMvc.perform(get(second)).andExpect(status().isNotFound());
    }
}
