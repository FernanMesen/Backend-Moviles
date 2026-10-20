package ApunteIA.BackEnd.security

import ApunteIA.BackEnd.entity.UserStatus
import ApunteIA.BackEnd.repository.UserRepository
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Lee `Authorization: Bearer <jwt>` en cada petición.
 *  - Sin header → sigue sin autenticar (las rutas públicas pasan; las protegidas responden 401).
 *  - Token válido y cuenta ACTIVE → deja al usuario autenticado con la autoridad ROLE_<rol>.
 *  - Token vencido, alterado o de una cuenta BLOCKED/DELETED → no autentica y guarda el motivo
 *    para que el RestAuthenticationEntryPoint lo devuelva en el 401.
 */
@Component
class JwtAuthenticationFilter(
    private val jwtService: JwtService,
    private val userRepository: UserRepository,
) : OncePerRequestFilter() {

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val header = request.getHeader(HttpHeaders.AUTHORIZATION)
        if (header == null || !header.startsWith(BEARER_PREFIX, ignoreCase = true)) {
            if (header != null) request.setAttribute(ERROR_ATTRIBUTE, "El header Authorization debe tener el formato 'Bearer <token>'")
            chain.doFilter(request, response)
            return
        }

        try {
            val tokenUser = jwtService.parse(header.substring(BEARER_PREFIX.length).trim())

            // Se consulta la base para que un usuario bloqueado o eliminado pierda el acceso de inmediato,
            // aunque su token todavía no haya vencido.
            val user = userRepository.findById(tokenUser.id).orElse(null)
            if (user == null || user.status != UserStatus.ACTIVE) {
                throw InvalidTokenException("La cuenta del token ya no está activa")
            }

            val authentication = UsernamePasswordAuthenticationToken(
                tokenUser, null, listOf(SimpleGrantedAuthority("ROLE_${tokenUser.role}")),
            )
            authentication.details = WebAuthenticationDetailsSource().buildDetails(request)
            SecurityContextHolder.getContext().authentication = authentication
        } catch (ex: InvalidTokenException) {
            SecurityContextHolder.clearContext()
            request.setAttribute(ERROR_ATTRIBUTE, ex.message)
        }

        chain.doFilter(request, response)
    }

    companion object {
        const val BEARER_PREFIX = "Bearer "
        /** Atributo de la petición con el motivo del rechazo del token. */
        const val ERROR_ATTRIBUTE = "apunteia.jwt.error"
    }
}