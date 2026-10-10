package com.restaurant.reservations.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.restaurant.reservations.model.Role;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

/**
 * Protege la emision y validacion de JWT: cualquier regresion aqui permite
 * falsificar sesiones o aceptar tokens caducados.
 */
class JwtTokenProviderTest {

    private final String secret = "unit-test-secret-".repeat(5); // 85 bytes, >= 64 exigidos por HS512
    private final JwtTokenProvider provider = new JwtTokenProvider(secret, 60_000);

    private static UsernamePasswordAuthenticationToken auth(long id, Role role, Long restaurantId) {
        UserPrincipal principal = new UserPrincipal(id, "mesero@example.test", "hash", role, restaurantId);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    private static UsernamePasswordAuthenticationToken auth() {
        return auth(42, Role.EMPLOYEE, 7L);
    }

    @Test
    @DisplayName("token generado es valido y conserva id, rol y restaurante")
    void tokenGeneradoConservaIdRolYRestaurante() {
        String token = provider.generateToken(auth(42, Role.COOK, 7L));

        assertTrue(provider.validateToken(token));
        assertEquals(42L, provider.getUserIdFromToken(token));
        assertEquals("COOK", provider.getRoleFromToken(token));
        assertEquals(7L, provider.getRestaurantIdFromToken(token));
    }

    @Test
    @DisplayName("restaurantId ausente se devuelve como null")
    void restaurantIdAusenteEsNull() {
        String token = provider.generateToken(auth(42, Role.EMPLOYEE, null));
        assertNull(provider.getRestaurantIdFromToken(token));
    }

    @Test
    @DisplayName("cada token lleva un jti distinto")
    void cadaTokenLlevaJtiDistinto() {
        String first = provider.generateToken(auth());
        String second = provider.generateToken(auth());
        assertNotEquals(first, second);
    }

    @Test
    @DisplayName("token con firma alterada es rechazado")
    void tokenConFirmaAlteradaEsRechazado() {
        String[] parts = provider.generateToken(auth()).split("\\.");
        String tampered = parts[0] + "." + parts[1] + "." + new StringBuilder(parts[2]).reverse();

        assertFalse(provider.validateToken(tampered));
    }

    @Test
    @DisplayName("token con payload alterado es rechazado")
    void tokenConPayloadAlteradoEsRechazado() {
        String[] token = provider.generateToken(auth(1, Role.EMPLOYEE, 7L)).split("\\.");
        String[] other = provider.generateToken(auth(999, Role.EMPLOYEE, 7L)).split("\\.");
        String forged = token[0] + "." + other[1] + "." + token[2];

        assertFalse(provider.validateToken(forged));
    }

    @Test
    @DisplayName("token firmado con otra clave es rechazado")
    void tokenFirmadoConOtraClaveEsRechazado() {
        JwtTokenProvider foreign = new JwtTokenProvider("otra-clave-distinta-".repeat(5), 60_000);
        assertFalse(provider.validateToken(foreign.generateToken(auth())));
    }

    @Test
    @DisplayName("token caducado es rechazado")
    void tokenCaducadoEsRechazado() {
        JwtTokenProvider expiredProvider = new JwtTokenProvider(secret, -1_000);
        assertFalse(expiredProvider.validateToken(expiredProvider.generateToken(auth())));
    }

    @Test
    @DisplayName("token con emisor distinto es rechazado")
    void tokenConEmisorDistintoEsRechazado() {
        String token = Jwts.builder()
            .subject("42")
            .issuer("otro-emisor")
            .expiration(new Date(System.currentTimeMillis() + 60_000))
            .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
            .compact();

        assertFalse(provider.validateToken(token));
    }

    @Test
    @DisplayName("token sin firma (alg none) es rechazado")
    void tokenSinFirmaEsRechazado() {
        String unsigned = Jwts.builder()
            .subject("42")
            .issuer("restaurant-reservations")
            .expiration(new Date(System.currentTimeMillis() + 60_000))
            .compact();

        assertFalse(provider.validateToken(unsigned));
    }

    @Test
    @DisplayName("entradas basura o vacias no son validas")
    void entradasBasuraOVaciasNoSonValidas() {
        assertFalse(provider.validateToken("no-es-un-jwt"));
        assertFalse(provider.validateToken(""));
    }

    @Test
    @DisplayName("secreto mas corto que 64 bytes falla rapido")
    void secretoCortoFallaRapido() {
        JwtTokenProvider weak = new JwtTokenProvider("demasiado-corta", 60_000);
        assertThrows(IllegalStateException.class, () -> weak.generateToken(auth()));
    }

    @Test
    @DisplayName("sin secreto se usa una clave efimera que no valida tokens de otra instancia")
    void sinSecretoSeUsaClaveEfimera() {
        JwtTokenProvider first = new JwtTokenProvider("", 60_000);
        JwtTokenProvider second = new JwtTokenProvider("   ", 60_000);
        String token = first.generateToken(auth());

        assertTrue(first.validateToken(token));
        assertFalse(second.validateToken(token));
    }
}
