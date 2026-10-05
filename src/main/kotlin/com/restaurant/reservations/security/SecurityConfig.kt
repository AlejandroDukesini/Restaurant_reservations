package com.restaurant.reservations.security

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
<<<<<<< HEAD
=======
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy
import org.springframework.security.web.header.writers.StaticHeadersWriter
>>>>>>> ed340704ed016e6dcc9e8c59c76220b3ee9c292e
import org.springframework.web.cors.CorsConfigurationSource

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
class SecurityConfig(
    private val jwtAuthenticationEntryPoint: JwtAuthenticationEntryPoint,
    private val jwtAuthenticationFilter: JwtAuthenticationFilter,
    private val corsConfigurationSource: CorsConfigurationSource
) {
    
    // Coste 12 (por defecto 10): ~4x mas trabajo por intento de fuerza bruta
    // offline si alguna vez se filtra la tabla de usuarios. Los hashes con coste
    // 10 ya existentes siguen validando: BCrypt lee el coste del propio hash.
    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder(12)


    @Bean
    fun authenticationManager(authenticationConfiguration: AuthenticationConfiguration): AuthenticationManager {
        return authenticationConfiguration.authenticationManager
    }
    
    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .cors { it.configurationSource(corsConfigurationSource) }
<<<<<<< HEAD
=======
            // CSRF deshabilitado de forma segura: la sesion es STATELESS y la
            // credencial viaja en la cabecera Authorization, que el navegador no
            // adjunta automaticamente en peticiones cross-site. Si algun dia se
            // pasa el token a una cookie, CSRF debe volver a activarse.
>>>>>>> ed340704ed016e6dcc9e8c59c76220b3ee9c292e
            .csrf { it.disable() }
            .headers { headers ->
                // Defensa en profundidad del navegador: sin CSP, cualquier XSS
                // reflejado o almacenado se ejecuta sin restriccion de origen.
                headers.contentSecurityPolicy {
                    it.policyDirectives(
                        "default-src 'self'; " +
                            "script-src 'self'; " +
                            // React/Tailwind inyectan estilos en linea en runtime.
                            "style-src 'self' 'unsafe-inline'; " +
                            "img-src 'self' data:; " +
                            "font-src 'self' data:; " +
                            "connect-src 'self'; " +
                            "object-src 'none'; " +
                            "base-uri 'self'; " +
                            "form-action 'self'; " +
                            "frame-ancestors 'none'"
                    )
                }
                // Clickjacking: equivalente moderno de X-Frame-Options DENY.
                headers.frameOptions { it.deny() }
                headers.referrerPolicy { it.policy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN) }
                // HSTS: solo lo aplica el navegador sobre HTTPS. Railway termina TLS.
                headers.httpStrictTransportSecurity {
                    it.includeSubDomains(true).maxAgeInSeconds(31_536_000)
                }
                headers.addHeaderWriter(
                    StaticHeadersWriter("Permissions-Policy", "geolocation=(), microphone=(), camera=(), payment=()")
                )
                headers.addHeaderWriter(
                    StaticHeadersWriter("Cross-Origin-Opener-Policy", "same-origin")
                )
            }
            .exceptionHandling { it.authenticationEntryPoint(jwtAuthenticationEntryPoint) }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { authz ->
                authz
                    .requestMatchers("/api/auth/**").permitAll()
                    .requestMatchers("/api/public/**").permitAll()
                    .requestMatchers("/api/admin/**").hasRole("ADMIN")
                    .requestMatchers("/api/employee/**").hasAnyRole("ADMIN", "EMPLOYEE")
                    .requestMatchers("/api/cook/**").hasAnyRole("ADMIN", "COOK")
                    .requestMatchers("/api/staff/**").hasAnyRole("ADMIN", "EMPLOYEE", "COOK")
                    .requestMatchers("/api/customer/**").hasAnyRole("ADMIN", "EMPLOYEE", "CUSTOMER")
                    // Cualquier otra ruta /api/** sigue requiriendo autenticacion.
                    .requestMatchers("/api/**").authenticated()
                    // El resto son los archivos estaticos del SPA (index.html, assets,
                    // y las rutas de React Router): deben ser publicos o el navegador
                    // recibe 401 y la pagina queda en blanco.
                    .anyRequest().permitAll()
            }
        
        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)
        
        return http.build()
    }
}
