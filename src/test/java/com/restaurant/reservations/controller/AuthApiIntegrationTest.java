package com.restaurant.reservations.controller;

import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.restaurant.reservations.model.Restaurant;
import com.restaurant.reservations.model.Role;
import com.restaurant.reservations.model.User;
import com.restaurant.reservations.repository.UserRepository;
import com.restaurant.reservations.support.IntegrationTest;
import com.restaurant.reservations.support.TestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

/** Contrato y reglas de seguridad de /api/auth (registro, login y sesion actual). */
class AuthApiIntegrationTest extends IntegrationTest {

    @Autowired private UserRepository userRepository;

    @Test
    @DisplayName("registro crea siempre un CUSTOMER aunque el cuerpo pida ADMIN y no devuelve el hash")
    void registroCreaSiempreUnCustomer() throws Exception {
        MvcResult result = mockMvc.perform(
                post("/api/auth/register").with(json(fields(
                    "email", "Nuevo.Cliente@Example.TEST",
                    "password", TestData.PASSWORD,
                    "name", "  Cliente Nuevo  ",
                    "role", "ADMIN",
                    "restaurantId", 1
                )))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.role").value("CUSTOMER"))
            .andExpect(jsonPath("$.email").value("nuevo.cliente@example.test"))
            .andExpect(jsonPath("$.name").value("Cliente Nuevo"))
            .andExpect(jsonPath("$.password").doesNotExist())
            .andReturn();

        User stored = userRepository.findById(body(result).get("id").asLong()).orElseThrow();
        assertEquals(Role.CUSTOMER, stored.getRole());
        assertNull(stored.getRestaurant());
        assertNotEquals(TestData.PASSWORD, stored.getPassword());
        assertTrue(stored.getPassword().startsWith("$2a$12$"), "hash BCrypt con coste 12");
    }

    @Test
    @DisplayName("registro con email ya existente responde 400")
    void registroConEmailExistenteResponde400() throws Exception {
        data.user(Role.CUSTOMER, null, "repetido@example.test");

        mockMvc.perform(
                post("/api/auth/register").with(json(fields(
                    "email", "REPETIDO@example.test", "password", TestData.PASSWORD, "name", "X"
                )))
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Email already registered"));
    }

    @Test
    @DisplayName("registro con contrasena corta responde 400 con el detalle del campo")
    void registroConContrasenaCortaResponde400() throws Exception {
        mockMvc.perform(
                post("/api/auth/register").with(json(fields(
                    "email", "corta@example.test", "password", "corta", "name", "X"
                )))
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Validation failed"))
            .andExpect(jsonPath("$.details[0]").value(startsWith("password:")));

        assertFalse(userRepository.findByEmail("corta@example.test").isPresent());
    }

    @Test
    @DisplayName("login correcto devuelve JWT valido con rol y restaurante")
    void loginCorrectoDevuelveJwtValido() throws Exception {
        Restaurant restaurant = data.restaurant();
        User cook = data.user(Role.COOK, restaurant, "chef@example.test");

        MvcResult result = mockMvc.perform(
                post("/api/auth/login").with(json(fields("email", "  CHEF@example.test ", "password", TestData.PASSWORD)))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.type").value("Bearer"))
            .andExpect(jsonPath("$.role").value("COOK"))
            .andExpect(jsonPath("$.userId").value(cook.getId()))
            .andExpect(jsonPath("$.restaurantId").value(restaurant.getId()))
            .andReturn();

        String token = body(result).get("token").asText();
        assertTrue(tokenProvider.validateToken(token));
        assertEquals(cook.getId(), tokenProvider.getUserIdFromToken(token));
    }

    @Test
    @DisplayName("contrasena incorrecta y usuario inexistente dan la misma respuesta 401")
    void contrasenaIncorrectaYUsuarioInexistenteDanLaMismaRespuesta() throws Exception {
        data.user(Role.EMPLOYEE, data.restaurant(), "mesero@example.test");

        String wrongPassword = mockMvc.perform(
                post("/api/auth/login").with(json(fields("email", "mesero@example.test", "password", "incorrecta-123")))
            )
            .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();

        String unknownUser = mockMvc.perform(
                post("/api/auth/login").with(json(fields("email", "nadie@example.test", "password", "incorrecta-123")))
            )
            .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();

        // Respuestas identicas: no permiten enumerar cuentas validas.
        assertEquals(wrongPassword, unknownUser);
        assertTrue(wrongPassword.contains("Invalid credentials"));
    }

    @Test
    @DisplayName("usuario desactivado no puede iniciar sesion")
    void usuarioDesactivadoNoPuedeIniciarSesion() throws Exception {
        data.user(Role.EMPLOYEE, data.restaurant(), "baja@example.test", false);

        mockMvc.perform(
                post("/api/auth/login").with(json(fields("email", "baja@example.test", "password", TestData.PASSWORD)))
            )
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("JSON mal formado responde 400 y no 500")
    void jsonMalFormadoResponde400() throws Exception {
        mockMvc.perform(post("/api/auth/login").with(json("{\"email\": ")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("cuerpo sin campos obligatorios responde 400")
    void cuerpoSinCamposObligatoriosResponde400() throws Exception {
        mockMvc.perform(post("/api/auth/login").with(json("{}")))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("me devuelve el usuario y el rol vigentes del token")
    void meDevuelveElUsuarioYElRolVigentes() throws Exception {
        Restaurant restaurant = data.restaurant();
        User cook = data.user(Role.COOK, restaurant, "chef@example.test");

        mockMvc.perform(get("/api/auth/me").with(authAs(cook)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.userId").value(cook.getId()))
            .andExpect(jsonPath("$.email").value("chef@example.test"))
            .andExpect(jsonPath("$.role").value("COOK"))
            .andExpect(jsonPath("$.restaurantId").value(restaurant.getId()));
    }

    @Test
    @DisplayName("me sin token o con usuario dado de baja responde 401")
    void meSinTokenOConUsuarioDadoDeBajaResponde401() throws Exception {
        User inactive = data.user(Role.EMPLOYEE, data.restaurant(), "baja@example.test", false);

        mockMvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/auth/me").with(authAs(inactive))).andExpect(status().isUnauthorized());
    }
}
