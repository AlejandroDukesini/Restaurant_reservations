package com.restaurant.reservations.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

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
class RateLimitingFilter(
    @Value("\${app.rate-limit.enabled:true}") private val enabled: Boolean,
    @Value("\${app.rate-limit.login-attempts-per-minute:10}") private val loginLimit: Int,
    @Value("\${app.rate-limit.public-writes-per-minute:20}") private val publicLimit: Int,
    @Value("\${app.rate-limit.trust-forwarded-header:true}") private val trustForwardedHeader: Boolean
) : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(RateLimitingFilter::class.java)
    private val counters = ConcurrentHashMap<String, Window>()

    private class Window(@Volatile var startedAt: Instant, val hits: AtomicInteger)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val limit = limitFor(request)
        if (!enabled || limit == null) {
            filterChain.doFilter(request, response)
            return
        }

        val clientIp = clientIp(request)
        val bucketKey = "${request.method}:${request.requestURI}:$clientIp"

        if (exceeds(bucketKey, limit)) {
            // Evidencia principal para detectar fuerza bruta: una sola linea por
            // bloqueo, con la ruta y la IP, sin datos de la peticion.
            log.warn(
                "Rate limit superado: ip={} ruta={} limite={}/min",
                clientIp, request.requestURI, limit
            )
            response.status = HttpStatus.TOO_MANY_REQUESTS.value()
            response.contentType = MediaType.APPLICATION_JSON_VALUE
            response.setHeader("Retry-After", "60")
            response.writer.write(
                """{"status":429,"error":"Too Many Requests","message":"Demasiadas peticiones. Intenta de nuevo en un minuto.","details":[]}"""
            )
            return
        }

        filterChain.doFilter(request, response)
    }

    private fun limitFor(request: HttpServletRequest): Int? {
        if (!request.method.equals("POST", ignoreCase = true)) return null
        val path = request.requestURI
        return when {
            path.startsWith("/api/auth/") -> loginLimit
            path.startsWith("/api/public/") -> publicLimit
            else -> null
        }
    }

    private fun exceeds(key: String, limit: Int): Boolean {
        val now = Instant.now()
        val window = counters.compute(key) { _, existing ->
            if (existing == null || Duration.between(existing.startedAt, now) >= WINDOW) {
                Window(now, AtomicInteger(0))
            } else {
                existing
            }
        }!!

        // Poda perezosa: sin esto el mapa crece sin limite con IPs rotativas
        // y se convierte en un vector de agotamiento de memoria.
        if (counters.size > MAX_TRACKED_KEYS) {
            counters.entries.removeIf { Duration.between(it.value.startedAt, now) >= WINDOW }
        }

        return window.hits.incrementAndGet() > limit
    }

    private fun clientIp(request: HttpServletRequest): String {
        if (trustForwardedHeader) {
            val forwarded = request.getHeader("X-Forwarded-For")
            if (!forwarded.isNullOrBlank()) {
                // El primer valor es el cliente original; el resto son proxies.
                return forwarded.split(",").first().trim().take(45)
            }
        }
        return request.remoteAddr ?: "desconocida"
    }

    private companion object {
        val WINDOW: Duration = Duration.ofMinutes(1)
        const val MAX_TRACKED_KEYS = 10_000
    }
}
