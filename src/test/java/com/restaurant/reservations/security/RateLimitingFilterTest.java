package com.restaurant.reservations.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Protege el limitador de fuerza bruta de login y de spam en endpoints publicos.
 * Una regresion aqui deja el login expuesto a password spraying sin freno.
 */
class RateLimitingFilterTest {

    private static final String LOGIN = "/api/auth/login";

    private static RateLimitingFilter filter(boolean enabled, int loginLimit, int publicLimit, boolean trustForwarded) {
        return new RateLimitingFilter(enabled, loginLimit, publicLimit, trustForwarded);
    }

    private record Outcome(int status, boolean passedThrough, MockHttpServletResponse response) {
    }

    private static Outcome send(RateLimitingFilter filter, String method, String uri, String ip, String forwardedFor)
        throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setRemoteAddr(ip);
        if (forwardedFor != null) {
            request.addHeader("X-Forwarded-For", forwardedFor);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        return new Outcome(response.getStatus(), chain.getRequest() != null, response);
    }

    private static Outcome send(RateLimitingFilter filter) throws Exception {
        return send(filter, "POST", LOGIN, "10.0.0.1", null);
    }

    @Test
    @DisplayName("permite el login hasta el limite y bloquea el siguiente con 429")
    void permiteHastaElLimiteYBloqueaElSiguiente() throws Exception {
        RateLimitingFilter f = filter(true, 3, 2, false);

        for (int i = 1; i <= 3; i++) {
            assertTrue(send(f).passedThrough(), "intento " + i + " debia pasar");
        }
        Outcome blocked = send(f);

        assertEquals(429, blocked.status());
        assertFalse(blocked.passedThrough());
        assertEquals("60", blocked.response().getHeader("Retry-After"));
        assertTrue(blocked.response().getContentAsString().contains("\"status\":429"));
    }

    @Test
    @DisplayName("los endpoints publicos usan su propio limite")
    void endpointsPublicosUsanSuPropioLimite() throws Exception {
        RateLimitingFilter f = filter(true, 3, 2, false);
        String uri = "/api/public/reservations";

        assertTrue(send(f, "POST", uri, "10.0.0.1", null).passedThrough());
        assertTrue(send(f, "POST", uri, "10.0.0.1", null).passedThrough());
        assertEquals(429, send(f, "POST", uri, "10.0.0.1", null).status());
    }

    @Test
    @DisplayName("GET y rutas no sensibles nunca se limitan")
    void getYRutasNoSensiblesNuncaSeLimitan() throws Exception {
        RateLimitingFilter f = filter(true, 1, 1, false);

        for (int i = 0; i < 5; i++) {
            assertTrue(send(f, "GET", "/api/public/restaurants", "10.0.0.1", null).passedThrough());
            assertTrue(send(f, "POST", "/api/employee/orders", "10.0.0.1", null).passedThrough());
        }
    }

    @Test
    @DisplayName("cada IP tiene su propio contador")
    void cadaIpTieneSuPropioContador() throws Exception {
        RateLimitingFilter f = filter(true, 1, 2, false);

        assertTrue(send(f, "POST", LOGIN, "10.0.0.1", null).passedThrough());
        assertEquals(429, send(f, "POST", LOGIN, "10.0.0.1", null).status());
        assertTrue(send(f, "POST", LOGIN, "10.0.0.2", null).passedThrough());
    }

    @Test
    @DisplayName("desactivado no bloquea nunca")
    void desactivadoNoBloqueaNunca() throws Exception {
        RateLimitingFilter f = filter(false, 1, 2, false);
        for (int i = 0; i < 5; i++) {
            assertTrue(send(f).passedThrough());
        }
    }

    @Test
    @DisplayName("sin confianza en el proxy se ignora X-Forwarded-For y rotar la cabecera no evita el bloqueo")
    void sinConfianzaEnElProxySeIgnoraXForwardedFor() throws Exception {
        RateLimitingFilter f = filter(true, 1, 2, false);

        assertTrue(send(f, "POST", LOGIN, "10.0.0.1", "1.1.1.1").passedThrough());
        assertEquals(429, send(f, "POST", LOGIN, "10.0.0.1", "2.2.2.2").status());
    }

    @Test
    @DisplayName("con confianza en el proxy se usa la primera IP de X-Forwarded-For")
    void conConfianzaEnElProxySeUsaLaPrimeraIp() throws Exception {
        RateLimitingFilter f = filter(true, 1, 2, true);

        assertTrue(send(f, "POST", LOGIN, "10.9.9.9", "203.0.113.5, 10.9.9.9").passedThrough());
        Outcome blocked = send(f, "POST", LOGIN, "10.9.9.9", "203.0.113.5");
        assertEquals(429, blocked.status());
        // Otra IP de cliente detras del mismo proxy no comparte el contador.
        assertTrue(send(f, "POST", LOGIN, "10.9.9.9", "198.51.100.7").passedThrough());
    }

    @Test
    @DisplayName("respuesta 429 es JSON con el formato de error de la API")
    void respuesta429EsJson() throws Exception {
        RateLimitingFilter f = filter(true, 0, 2, false);
        Outcome outcome = send(f);

        assertEquals(429, outcome.status());
        assertNotNull(outcome.response().getContentType());
        assertTrue(outcome.response().getContentType().startsWith("application/json"));
        assertNull(outcome.response().getHeader("Set-Cookie"));
    }
}
