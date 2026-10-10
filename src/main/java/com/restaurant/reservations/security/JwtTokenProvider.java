package com.restaurant.reservations.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.IncorrectClaimException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.MissingClaimException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);
    private static final int MIN_SECRET_BYTES = 64;
    private static final String ISSUER = "restaurant-reservations";

    private final String jwtSecret;
    private final long jwtExpirationInMs;

    // Se resuelve en el primer uso (como el `by lazy` original): un secreto corto no
    // impide construir el bean, pero falla en cuanto se intenta firmar o validar.
    private volatile SecretKey key;

    public JwtTokenProvider(
        @Value("${jwt.secret:}") String jwtSecret,
        @Value("${jwt.expiration}") long jwtExpirationInMs
    ) {
        this.jwtSecret = jwtSecret;
        this.jwtExpirationInMs = jwtExpirationInMs;
    }

    // HS512 necesita una clave de >= 64 bytes. Si JWT_SECRET no se define (solo deberia
    // ocurrir en desarrollo) se genera una clave aleatoria en memoria: la app arranca,
    // pero los tokens dejan de ser validos al reiniciar. Asi no queda ningun secreto
    // por defecto en el repositorio que sirva para firmar tokens en produccion.
    private SecretKey key() {
        SecretKey current = key;
        if (current == null) {
            synchronized (this) {
                if (key == null) {
                    key = buildKey();
                }
                current = key;
            }
        }
        return current;
    }

    private SecretKey buildKey() {
        String secret = jwtSecret == null ? "" : jwtSecret.trim();
        if (secret.isEmpty()) {
            log.warn(
                "JWT_SECRET no esta definido: se genera una clave efimera en memoria. "
                    + "Los tokens se invalidan en cada reinicio. Define JWT_SECRET (>= 64 bytes) en produccion."
            );
            return Jwts.SIG.HS512.key().build();
        }
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_BYTES) {
            // Fallar rapido: una clave corta permite fuerza bruta sobre la firma.
            throw new IllegalStateException(
                "JWT_SECRET debe tener al menos " + MIN_SECRET_BYTES + " bytes para HS512."
            );
        }
        return Keys.hmacShaKeyFor(bytes);
    }

    public String generateToken(Authentication authentication) {
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpirationInMs);

        return Jwts.builder()
            .subject(String.valueOf(userPrincipal.getId()))
            // jti unico: permite correlacionar el token en logs y revocarlo si hiciera falta.
            .id(UUID.randomUUID().toString())
            .claim("email", userPrincipal.getEmail())
            .claim("role", userPrincipal.getRole())
            .claim("restaurantId", userPrincipal.getRestaurantId())
            .issuer(ISSUER)
            .issuedAt(now)
            .expiration(expiryDate)
            .signWith(key(), Jwts.SIG.HS512)
            .compact();
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
            .verifyWith(key())
            .requireIssuer(ISSUER)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    public Long getUserIdFromToken(String token) {
        return Long.parseLong(parseClaims(token).getSubject());
    }

    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (io.jsonwebtoken.security.SecurityException ex) {
            // Firma invalida: patron tipico de manipulacion del token.
            log.warn("JWT rechazado: firma invalida");
        } catch (MalformedJwtException ex) {
            log.warn("JWT rechazado: token malformado");
        } catch (ExpiredJwtException ex) {
            log.debug("JWT rechazado: token expirado");
        } catch (UnsupportedJwtException ex) {
            log.warn("JWT rechazado: algoritmo o formato no soportado");
        } catch (IncorrectClaimException ex) {
            log.warn("JWT rechazado: claim invalido ({})", ex.getClaimName());
        } catch (MissingClaimException ex) {
            log.warn("JWT rechazado: falta el claim {}", ex.getClaimName());
        } catch (IllegalArgumentException ex) {
            log.warn("JWT rechazado: token vacio");
        }
        return false;
    }

    public String getRoleFromToken(String token) {
        return (String) parseClaims(token).get("role");
    }

    // Jackson deserializa los numeros pequenos del claim como Integer: un cast a Long fallaria.
    public Long getRestaurantIdFromToken(String token) {
        return parseClaims(token).get("restaurantId") instanceof Number number ? number.longValue() : null;
    }
}
