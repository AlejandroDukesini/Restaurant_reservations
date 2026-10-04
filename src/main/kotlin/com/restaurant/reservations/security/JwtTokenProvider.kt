package com.restaurant.reservations.security

import io.jsonwebtoken.*
import io.jsonwebtoken.security.Keys
// Sin este alias, `SecurityException` resuelve a java.lang.SecurityException y la
// excepcion de firma invalida de JJWT escapaba de validateToken en vez de dar false.
import io.jsonwebtoken.security.SecurityException as JwtSecurityException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Component
import java.util.*
import javax.crypto.SecretKey

@Component
class JwtTokenProvider(
    @Value("\${jwt.secret:}") private val jwtSecret: String,
    @Value("\${jwt.expiration}") private val jwtExpirationInMs: Long
) {

    private val log = LoggerFactory.getLogger(JwtTokenProvider::class.java)

    // HS512 necesita una clave de >= 64 bytes. Si JWT_SECRET no se define (solo deberia
    // ocurrir en desarrollo) se genera una clave aleatoria en memoria: la app arranca,
    // pero los tokens dejan de ser validos al reiniciar. Asi no queda ningun secreto
    // por defecto en el repositorio que sirva para firmar tokens en produccion.
    private val key: SecretKey by lazy {
        val secret = jwtSecret.trim()
        when {
            secret.isEmpty() -> {
                log.warn(
                    "JWT_SECRET no esta definido: se genera una clave efimera en memoria. " +
                        "Los tokens se invalidan en cada reinicio. Define JWT_SECRET (>= 64 bytes) en produccion."
                )
                Keys.secretKeyFor(SignatureAlgorithm.HS512)
            }
            secret.toByteArray(Charsets.UTF_8).size < MIN_SECRET_BYTES -> {
                // Fallar rapido: una clave corta permite fuerza bruta sobre la firma.
                throw IllegalStateException(
                    "JWT_SECRET debe tener al menos $MIN_SECRET_BYTES bytes para HS512."
                )
            }
            else -> Keys.hmacShaKeyFor(secret.toByteArray(Charsets.UTF_8))
        }
    }

    fun generateToken(authentication: Authentication): String {
        val userPrincipal = authentication.principal as UserPrincipal
        val now = Date()
        val expiryDate = Date(now.time + jwtExpirationInMs)

        return Jwts.builder()
            .setSubject(userPrincipal.id.toString())
            // jti unico: permite correlacionar el token en logs y revocarlo si hiciera falta.
            .setId(UUID.randomUUID().toString())
            .claim("email", userPrincipal.email)
            .claim("role", userPrincipal.role)
            .claim("restaurantId", userPrincipal.restaurantId)
            .setIssuer(ISSUER)
            .setIssuedAt(now)
            .setExpiration(expiryDate)
            .signWith(key, SignatureAlgorithm.HS512)
            .compact()
    }

    private fun parseClaims(token: String): Claims =
        Jwts.parser()
            .setSigningKey(key)
            .requireIssuer(ISSUER)
            .build()
            .parseClaimsJws(token)
            .body

    fun getUserIdFromToken(token: String): Long = parseClaims(token).subject.toLong()

    fun validateToken(token: String): Boolean {
        try {
            parseClaims(token)
            return true
        } catch (ex: JwtSecurityException) {
            // Firma invalida: patron tipico de manipulacion del token.
            log.warn("JWT rechazado: firma invalida")
        } catch (ex: MalformedJwtException) {
            log.warn("JWT rechazado: token malformado")
        } catch (ex: ExpiredJwtException) {
            log.debug("JWT rechazado: token expirado")
        } catch (ex: UnsupportedJwtException) {
            log.warn("JWT rechazado: algoritmo o formato no soportado")
        } catch (ex: IncorrectClaimException) {
            log.warn("JWT rechazado: claim invalido ({})", ex.claimName)
        } catch (ex: MissingClaimException) {
            log.warn("JWT rechazado: falta el claim {}", ex.claimName)
        } catch (ex: IllegalArgumentException) {
            log.warn("JWT rechazado: token vacio")
        }
        return false
    }

    fun getRoleFromToken(token: String): String = parseClaims(token)["role"] as String

    // Jackson deserializa los numeros pequenos del claim como Integer: `as? Long` daba siempre null.
    fun getRestaurantIdFromToken(token: String): Long? = (parseClaims(token)["restaurantId"] as? Number)?.toLong()

    private companion object {
        const val MIN_SECRET_BYTES = 64
        const val ISSUER = "restaurant-reservations"
    }
}
