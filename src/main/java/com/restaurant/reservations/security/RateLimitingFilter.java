package com.restaurant.reservations.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Limitador de peticiones por IP para los endpoints que un atacante puede abusar
 * sin credenciales:
 *
 *  - POST bajo /api/auth  -> fuerza bruta, password spraying, alta masiva de cuentas
 *  - POST bajo /api/public -> spam de reservas y creacion masiva de clientes
 *
 * Implementacion con ventana fija en memoria, deliberadamente sin dependencias
 * nuevas. Limitaciones asumidas y documentadas en cibersegurity.txt:
 *  - el estado es por instancia: con varias replicas el limite efectivo se multiplica;
 *  - la IP se toma de X-Forwarded-For solo si se confia en el proxy (Railway lo fija).
 * Para produccion multi-instancia el control debe moverse a Redis o al edge (WAF).
 */
@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitingFilter.class);
    private static final Duration WINDOW = Duration.ofMinutes(1);
    private static final int MAX_TRACKED_KEYS = 10_000;
    private static final String TOO_MANY_REQUESTS_BODY =
        "{\"status\":429,\"error\":\"Too Many Requests\","
            + "\"message\":\"Demasiadas peticiones. Intenta de nuevo en un minuto.\",\"details\":[]}";

    private final boolean enabled;
    private final int loginLimit;
    private final int publicLimit;
    private final boolean trustForwardedHeader;
    private final ConcurrentHashMap<String, Window> counters = new ConcurrentHashMap<>();

    private record Window(Instant startedAt, AtomicInteger hits) {
    }

    public RateLimitingFilter(
        @Value("${app.rate-limit.enabled:true}") boolean enabled,
        @Value("${app.rate-limit.login-attempts-per-minute:10}") int loginLimit,
        @Value("${app.rate-limit.public-writes-per-minute:20}") int publicLimit,
        @Value("${app.rate-limit.trust-forwarded-header:true}") boolean trustForwardedHeader
    ) {
        this.enabled = enabled;
        this.loginLimit = loginLimit;
        this.publicLimit = publicLimit;
        this.trustForwardedHeader = trustForwardedHeader;
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        Integer limit = limitFor(request);
        if (!enabled || limit == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = clientIp(request);
        String bucketKey = request.getMethod() + ":" + request.getRequestURI() + ":" + clientIp;

        if (exceeds(bucketKey, limit)) {
            // Evidencia principal para detectar fuerza bruta: una sola linea por
            // bloqueo, con la ruta y la IP, sin datos de la peticion.
            log.warn(
                "Rate limit superado: ip={} ruta={} limite={}/min",
                clientIp, request.getRequestURI(), limit
            );
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setHeader("Retry-After", "60");
            response.getWriter().write(TOO_MANY_REQUESTS_BODY);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private Integer limitFor(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return null;
        }
        String path = request.getRequestURI();
        if (path.startsWith("/api/auth/")) {
            return loginLimit;
        }
        if (path.startsWith("/api/public/")) {
            return publicLimit;
        }
        return null;
    }

    private boolean exceeds(String key, int limit) {
        Instant now = Instant.now();
        Window window = counters.compute(key, (k, existing) ->
            existing == null || Duration.between(existing.startedAt(), now).compareTo(WINDOW) >= 0
                ? new Window(now, new AtomicInteger(0))
                : existing
        );

        // Poda perezosa: sin esto el mapa crece sin limite con IPs rotativas
        // y se convierte en un vector de agotamiento de memoria.
        if (counters.size() > MAX_TRACKED_KEYS) {
            counters.entrySet().removeIf(e -> Duration.between(e.getValue().startedAt(), now).compareTo(WINDOW) >= 0);
        }

        return window.hits().incrementAndGet() > limit;
    }

    private String clientIp(HttpServletRequest request) {
        if (trustForwardedHeader) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                // El primer valor es el cliente original; el resto son proxies.
                String first = forwarded.split(",", 2)[0].strip();
                return first.length() > 45 ? first.substring(0, 45) : first;
            }
        }
        String remoteAddr = request.getRemoteAddr();
        return remoteAddr != null ? remoteAddr : "desconocida";
    }
}
