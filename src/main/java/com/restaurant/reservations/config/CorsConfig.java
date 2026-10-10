package com.restaurant.reservations.config;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class CorsConfig {

    private final List<String> allowedOriginPatternList;

    public CorsConfig(
        // En produccion front y API comparten origen (el JAR sirve el build de React),
        // asi que no hace falta ningun origen extra. Los patrones de localhost existen
        // para el `vite dev` en la maquina del desarrollador; se pueden ampliar por
        // entorno con CORS_ALLOWED_ORIGINS sin tocar el codigo.
        @Value("${app.cors.allowed-origin-patterns:http://localhost:*,http://127.0.0.1:*}")
        List<String> allowedOriginPatternList
    ) {
        this.allowedOriginPatternList = allowedOriginPatternList;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(allowedOriginPatternList);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        // Lista explicita en vez de "*": con allowCredentials=true, aceptar
        // cualquier cabecera amplia lo que un origen permitido puede enviar.
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "Origin"));
        config.setExposedHeaders(List.of("Retry-After"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
