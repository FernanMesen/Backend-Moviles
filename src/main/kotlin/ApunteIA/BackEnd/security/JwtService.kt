package ApunteIA.BackEnd.security

import ApunteIA.BackEnd.entity.User
import io.jsonwebtoken.Claims
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Date
import javax.crypto.SecretKey

/**
 * Genera y valida los JWT de la API (reemplaza al antiguo TokenGenerator de token opaco).
 *
 * Formato del token (HS256):
 *   sub   = id del usuario        email = email del usuario
 *   role  = STUDENT | ADMIN       iss   = apunteia-backend
 *   iat   = emitido               exp   = emitido + expirationMinutes
 */
@Service
class JwtService(private val properties: JwtProperties) {

    private val log = LoggerFactory.getLogger(javaClass)

    private val key: SecretKey = run {
        val bytes = properties.secret.toByteArray(StandardCharsets.UTF_8)
        require(bytes.size >= MIN_SECRET_BYTES) {
            "app.jwt.secret (JWT_SECRET) debe tener al menos $MIN_SECRET_BYTES caracteres para HS256"
        }
        if (properties.secret.startsWith(DEV_SECRET_PREFIX)) {
            log.warn("Se está usando el JWT_SECRET de desarrollo. En la nube hay que definir la variable JWT_SECRET.")
        }
        Keys.hmacShaKeyFor(bytes)
    }

    /** Segundos de vida del token; se devuelve en el login como `expiresIn`. */
    val expirationSeconds: Long get() = properties.expirationMinutes * 60

    fun generate(user: User, issuedAt: Instant = Instant.now()): String {
        val id = requireNotNull(user.id) { "El usuario debe estar guardado para generar su token" }
        return Jwts.builder()
            .subject(id.toString())
            .issuer(properties.issuer)
            .claim(CLAIM_EMAIL, user.email)
            .claim(CLAIM_ROLE, user.role.name)
            .issuedAt(Date.from(issuedAt))
            .expiration(Date.from(issuedAt.plusSeconds(expirationSeconds)))
            .signWith(key, Jwts.SIG.HS256)
            .compact()
    }

    /**
     * Valida firma, emisor y expiración, y devuelve los datos del token.
     * @throws InvalidTokenException con un mensaje listo para mostrar en el 401.
     */
    fun parse(token: String): AuthenticatedUser {
        val claims: Claims = try {
            Jwts.parser()
                .verifyWith(key)
                .requireIssuer(properties.issuer)
                .build()
                .parseSignedClaims(token)
                .payload
        } catch (ex: ExpiredJwtException) {
            throw InvalidTokenException("El token expiró. Inicie sesión de nuevo")
        } catch (ex: JwtException) {
            throw InvalidTokenException("El token no es válido")
        } catch (ex: IllegalArgumentException) {
            throw InvalidTokenException("El token no es válido")
        }

        val id = claims.subject?.toLongOrNull() ?: throw InvalidTokenException("El token no es válido")
        val email = claims.get(CLAIM_EMAIL, String::class.java) ?: throw InvalidTokenException("El token no es válido")
        val role = claims.get(CLAIM_ROLE, String::class.java) ?: throw InvalidTokenException("El token no es válido")
        return AuthenticatedUser(id = id, email = email, role = role)
    }

    companion object {
        const val CLAIM_EMAIL = "email"
        const val CLAIM_ROLE = "role"
        const val MIN_SECRET_BYTES = 32
        /** El secreto por defecto de application.yml empieza así; solo sirve para desarrollo. */
        const val DEV_SECRET_PREFIX = "dev-only-"
    }
}

/** Token mal formado, vencido, alterado o de una cuenta que ya no está activa. */
class InvalidTokenException(message: String) : RuntimeException(message)