package com.restaurant.reservations.controller

import com.restaurant.reservations.model.Role
import com.restaurant.reservations.repository.UserRepository
import com.restaurant.reservations.support.IntegrationTest
import com.restaurant.reservations.support.TestData
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/** Contrato y reglas de seguridad de /api/auth (registro, login y sesion actual). */
class AuthApiIntegrationTest : IntegrationTest() {

    @Autowired private lateinit var userRepository: UserRepository

    @Test
    fun `registro crea siempre un CUSTOMER aunque el cuerpo pida ADMIN y no devuelve el hash`() {
        val result = mockMvc.perform(
            post("/api/auth/register").json(
                mapOf(
                    "email" to "Nuevo.Cliente@Example.TEST",
                    "password" to TestData.PASSWORD,
                    "name" to "  Cliente Nuevo  ",
                    "role" to "ADMIN",
                    "restaurantId" to 1
                )
            )
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.role").value("CUSTOMER"))
            .andExpect(jsonPath("$.email").value("nuevo.cliente@example.test"))
            .andExpect(jsonPath("$.name").value("Cliente Nuevo"))
            .andExpect(jsonPath("$.password").doesNotExist())
            .andReturn()

        val stored = userRepository.findById(result.body()["id"].asLong()).orElseThrow()
        assertEquals(Role.CUSTOMER, stored.role)
        assertEquals(null, stored.restaurant)
        assertNotEquals(TestData.PASSWORD, stored.password)
        assertTrue(stored.password.startsWith("$2a$12$"), "hash BCrypt con coste 12")
    }

    @Test
    fun `registro con email ya existente responde 400`() {
        data.user(Role.CUSTOMER, null, email = "repetido@example.test")

        mockMvc.perform(
            post("/api/auth/register").json(
                mapOf("email" to "REPETIDO@example.test", "password" to TestData.PASSWORD, "name" to "X")
            )
        ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("Email already registered"))
    }

    @Test
    fun `registro con contrasena corta responde 400 con el detalle del campo`() {
        mockMvc.perform(
            post("/api/auth/register").json(
                mapOf("email" to "corta@example.test", "password" to "corta", "name" to "X")
            )
        ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("Validation failed"))
            .andExpect(jsonPath("$.details[0]").value(org.hamcrest.Matchers.startsWith("password:")))

        assertFalse(userRepository.findByEmail("corta@example.test").isPresent)
    }

    @Test
    fun `login correcto devuelve JWT valido con rol y restaurante`() {
        val restaurant = data.restaurant()
        val cook = data.user(Role.COOK, restaurant, email = "chef@example.test")

        val result = mockMvc.perform(
            post("/api/auth/login").json(mapOf("email" to "  CHEF@example.test ", "password" to TestData.PASSWORD))
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.type").value("Bearer"))
            .andExpect(jsonPath("$.role").value("COOK"))
            .andExpect(jsonPath("$.userId").value(cook.id!!))
            .andExpect(jsonPath("$.restaurantId").value(restaurant.id!!))
            .andReturn()

        val token = result.body()["token"].asText()
        assertTrue(tokenProvider.validateToken(token))
        assertEquals(cook.id, tokenProvider.getUserIdFromToken(token))
    }

    @Test
    fun `contrasena incorrecta y usuario inexistente dan la misma respuesta 401`() {
        data.user(Role.EMPLOYEE, data.restaurant(), email = "mesero@example.test")

        val wrongPassword = mockMvc.perform(
            post("/api/auth/login").json(mapOf("email" to "mesero@example.test", "password" to "incorrecta-123"))
        ).andExpect(status().isUnauthorized).andReturn().response.contentAsString

        val unknownUser = mockMvc.perform(
            post("/api/auth/login").json(mapOf("email" to "nadie@example.test", "password" to "incorrecta-123"))
        ).andExpect(status().isUnauthorized).andReturn().response.contentAsString

        // Respuestas identicas: no permiten enumerar cuentas validas.
        assertEquals(wrongPassword, unknownUser)
        assertTrue(wrongPassword.contains("Invalid credentials"))
    }

    @Test
    fun `usuario desactivado no puede iniciar sesion`() {
        data.user(Role.EMPLOYEE, data.restaurant(), email = "baja@example.test", active = false)

        mockMvc.perform(
            post("/api/auth/login").json(mapOf("email" to "baja@example.test", "password" to TestData.PASSWORD))
        ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `JSON mal formado responde 400 y no 500`() {
        mockMvc.perform(post("/api/auth/login").json("{\"email\": "))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.status").value(400))
    }

    @Test
    fun `cuerpo sin campos obligatorios responde 400`() {
        mockMvc.perform(post("/api/auth/login").json("{}"))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `me devuelve el usuario y el rol vigentes del token`() {
        val restaurant = data.restaurant()
        val cook = data.user(Role.COOK, restaurant, email = "chef@example.test")

        mockMvc.perform(get("/api/auth/me").authAs(cook))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.userId").value(cook.id!!))
            .andExpect(jsonPath("$.email").value("chef@example.test"))
            .andExpect(jsonPath("$.role").value("COOK"))
            .andExpect(jsonPath("$.restaurantId").value(restaurant.id!!))
    }

    @Test
    fun `me sin token o con usuario dado de baja responde 401`() {
        val inactive = data.user(Role.EMPLOYEE, data.restaurant(), email = "baja@example.test", active = false)

        mockMvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized)
        mockMvc.perform(get("/api/auth/me").authAs(inactive)).andExpect(status().isUnauthorized)
    }
}
