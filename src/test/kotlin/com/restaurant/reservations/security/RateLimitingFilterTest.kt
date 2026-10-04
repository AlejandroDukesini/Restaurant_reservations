package com.restaurant.reservations.security

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

/**
 * Protege el limitador de fuerza bruta de login y de spam en endpoints publicos.
 * Una regresion aqui deja el login expuesto a password spraying sin freno.
 */
class RateLimitingFilterTest {

    private fun filter(
        enabled: Boolean = true,
        loginLimit: Int = 3,
        publicLimit: Int = 2,
        trustForwarded: Boolean = false
    ) = RateLimitingFilter(enabled, loginLimit, publicLimit, trustForwarded)

    private data class Outcome(val status: Int, val passedThrough: Boolean, val response: MockHttpServletResponse)

    private fun send(
        filter: RateLimitingFilter,
        method: String = "POST",
        uri: String = "/api/auth/login",
        ip: String = "10.0.0.1",
        forwardedFor: String? = null
    ): Outcome {
        val request = MockHttpServletRequest(method, uri).apply {
            remoteAddr = ip
            forwardedFor?.let { addHeader("X-Forwarded-For", it) }
        }
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()
        filter.doFilter(request, response, chain)
        return Outcome(response.status, chain.request != null, response)
    }

    @Test
    fun `permite el login hasta el limite y bloquea el siguiente con 429`() {
        val f = filter(loginLimit = 3)

        repeat(3) { assertTrue(send(f).passedThrough, "intento ${it + 1} debia pasar") }
        val blocked = send(f)

        assertEquals(429, blocked.status)
        assertEquals(false, blocked.passedThrough)
        assertEquals("60", blocked.response.getHeader("Retry-After"))
        assertTrue(blocked.response.contentAsString.contains("\"status\":429"))
    }

    @Test
    fun `los endpoints publicos usan su propio limite`() {
        val f = filter(publicLimit = 2)
        val uri = "/api/public/reservations"

        assertTrue(send(f, uri = uri).passedThrough)
        assertTrue(send(f, uri = uri).passedThrough)
        assertEquals(429, send(f, uri = uri).status)
    }

    @Test
    fun `GET y rutas no sensibles nunca se limitan`() {
        val f = filter(loginLimit = 1, publicLimit = 1)

        repeat(5) {
            assertTrue(send(f, method = "GET", uri = "/api/public/restaurants").passedThrough)
            assertTrue(send(f, uri = "/api/employee/orders").passedThrough)
        }
    }

    @Test
    fun `cada IP tiene su propio contador`() {
        val f = filter(loginLimit = 1)

        assertTrue(send(f, ip = "10.0.0.1").passedThrough)
        assertEquals(429, send(f, ip = "10.0.0.1").status)
        assertTrue(send(f, ip = "10.0.0.2").passedThrough)
    }

    @Test
    fun `desactivado no bloquea nunca`() {
        val f = filter(enabled = false, loginLimit = 1)
        repeat(5) { assertTrue(send(f).passedThrough) }
    }

    @Test
    fun `sin confianza en el proxy se ignora X-Forwarded-For y rotar la cabecera no evita el bloqueo`() {
        val f = filter(loginLimit = 1, trustForwarded = false)

        assertTrue(send(f, forwardedFor = "1.1.1.1").passedThrough)
        assertEquals(429, send(f, forwardedFor = "2.2.2.2").status)
    }

    @Test
    fun `con confianza en el proxy se usa la primera IP de X-Forwarded-For`() {
        val f = filter(loginLimit = 1, trustForwarded = true)

        assertTrue(send(f, ip = "10.9.9.9", forwardedFor = "203.0.113.5, 10.9.9.9").passedThrough)
        val blocked = send(f, ip = "10.9.9.9", forwardedFor = "203.0.113.5")
        assertEquals(429, blocked.status)
        // Otra IP de cliente detras del mismo proxy no comparte el contador.
        assertTrue(send(f, ip = "10.9.9.9", forwardedFor = "198.51.100.7").passedThrough)
    }

    @Test
    fun `respuesta 429 es JSON con el formato de error de la API`() {
        val f = filter(loginLimit = 0)
        val outcome = send(f)

        assertEquals(429, outcome.status)
        assertNotNull(outcome.response.contentType)
        assertTrue(outcome.response.contentType!!.startsWith("application/json"))
        assertNull(outcome.response.getHeader("Set-Cookie"))
    }
}
