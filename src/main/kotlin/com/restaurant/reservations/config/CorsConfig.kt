package com.restaurant.reservations.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
class CorsConfig(
    // En produccion front y API comparten origen (el JAR sirve el build de React),
    // asi que no hace falta ningun origen extra. Los patrones de localhost existen
    // para el `vite dev` en la maquina del desarrollador; se pueden ampliar por
    // entorno con CORS_ALLOWED_ORIGINS sin tocar el codigo.
    @Value("\${app.cors.allowed-origin-patterns:http://localhost:*,http://127.0.0.1:*}")
    private val allowedOriginPatternList: List<String>
) {
    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val config = CorsConfiguration().apply {
            allowedOriginPatterns = allowedOriginPatternList
            allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
            // Lista explicita en vez de "*": con allowCredentials=true, aceptar
            // cualquier cabecera amplia lo que un origen permitido puede enviar.
            allowedHeaders = listOf("Authorization", "Content-Type", "Accept", "Origin")
            exposedHeaders = listOf("Retry-After")
            allowCredentials = true
            maxAge = 3600
        }
        return UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration("/api/**", config)
        }
    }
}
