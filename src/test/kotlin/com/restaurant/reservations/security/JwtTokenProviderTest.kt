package com.restaurant.reservations.security

import com.restaurant.reservations.model.Role
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import java.util.Date

/**
 * Protege la emision y validacion de JWT: cualquier regresion aqui permite
 * falsificar sesiones o aceptar tokens caducados.
 */
class JwtTokenProviderTest {

    private val secret = "unit-test-secret-".repeat(5) // 85 bytes, >= 64 exigidos por HS512
    private val provider = JwtTokenProvider(secret, 60_000)

    private fun auth(
        id: Long = 42,
        role: Role = Role.EMPLOYEE,
        restaurantId: Long? = 7
    ): UsernamePasswordAuthenticationToken {
        val principal = UserPrincipal(id, "mesero@example.test", "hash", role, restaurantId)
        return UsernamePasswordAuthenticationToken(principal, null, principal.authorities)
    }

    @Test
    fun `token generado es valido y conserva id, rol y restaurante`() {
        val token = provider.generateToken(auth(id = 42, role = Role.COOK, restaurantId = 7))

        assertTrue(provider.validateToken(token))
        assertEquals(42L, provider.getUserIdFromToken(token))
        assertEquals("COOK", provider.getRoleFromToken(token))
        assertEquals(7L, provider.getRestaurantIdFromToken(token))
    }

    @Test
    fun `restaurantId ausente se devuelve como null`() {
        val token = provider.generateToken(auth(restaurantId = null))
        assertNull(provider.getRestaurantIdFromToken(token))
    }

    @Test
    fun `cada token lleva un jti distinto`() {
        val first = provider.generateToken(auth())
        val second = provider.generateToken(auth())
        assertNotEquals(first, second)
    }

    @Test
    fun `token con firma alterada es rechazado`() {
        val token = provider.generateToken(auth())
        val (header, payload, signature) = token.split(".")
        val tampered = "$header.$payload.${signature.reversed()}"

        assertFalse(provider.validateToken(tampered))
    }

    @Test
    fun `token con payload alterado es rechazado`() {
        val token = provider.generateToken(auth(id = 1))
        val other = provider.generateToken(auth(id = 999))
        val (header, _, signature) = token.split(".")
        val forged = "$header.${other.split(".")[1]}.$signature"

        assertFalse(provider.validateToken(forged))
    }

    @Test
    fun `token firmado con otra clave es rechazado`() {
        val foreign = JwtTokenProvider("otra-clave-distinta-".repeat(5), 60_000)
        assertFalse(provider.validateToken(foreign.generateToken(auth())))
    }

    @Test
    fun `token caducado es rechazado`() {
        val expiredProvider = JwtTokenProvider(secret, -1_000)
        assertFalse(expiredProvider.validateToken(expiredProvider.generateToken(auth())))
    }

    @Test
    fun `token con emisor distinto es rechazado`() {
        val key = Keys.hmacShaKeyFor(secret.toByteArray())
        val token = Jwts.builder()
            .subject("42")
            .issuer("otro-emisor")
            .expiration(Date(System.currentTimeMillis() + 60_000))
            .signWith(key)
            .compact()

        assertFalse(provider.validateToken(token))
    }

    @Test
    fun `token sin firma (alg none) es rechazado`() {
        val unsigned = Jwts.builder()
            .subject("42")
            .issuer("restaurant-reservations")
            .expiration(Date(System.currentTimeMillis() + 60_000))
            .compact()

        assertFalse(provider.validateToken(unsigned))
    }

    @Test
    fun `entradas basura o vacias no son validas`() {
        assertFalse(provider.validateToken("no-es-un-jwt"))
        assertFalse(provider.validateToken(""))
    }

    @Test
    fun `secreto mas corto que 64 bytes falla rapido`() {
        val weak = JwtTokenProvider("demasiado-corta", 60_000)
        assertThrows<IllegalStateException> { weak.generateToken(auth()) }
    }

    @Test
    fun `sin secreto se usa una clave efimera que no valida tokens de otra instancia`() {
        val first = JwtTokenProvider("", 60_000)
        val second = JwtTokenProvider("   ", 60_000)
        val token = first.generateToken(auth())

        assertTrue(first.validateToken(token))
        assertFalse(second.validateToken(token))
    }
}
