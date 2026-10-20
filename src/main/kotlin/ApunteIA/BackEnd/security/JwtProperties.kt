package ApunteIA.BackEnd.security

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Configuración del JWT (`app.jwt.*` en application.yml).
 *  - secret: clave HMAC-SHA256, mínimo 32 caracteres. En la nube se pasa con la variable JWT_SECRET.
 *  - expirationMinutes: vida del token. Al vencer, el cliente vuelve a hacer login.
 *  - issuer: quién emite el token (claim `iss`); se valida al leerlo.
 */
@ConfigurationProperties(prefix = "app.jwt")
data class JwtProperties(
    val secret: String,
    val expirationMinutes: Long = 120,
    val issuer: String = "apunteia-backend",
)