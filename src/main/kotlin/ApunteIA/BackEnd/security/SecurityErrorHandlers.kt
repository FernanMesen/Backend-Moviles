package ApunteIA.BackEnd.security

import ApunteIA.BackEnd.exception.ErrorResponse
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.stereotype.Component

/**
 * Los rechazos de Spring Security ocurren en el filtro, antes de llegar al GlobalExceptionHandler.
 * Estas dos clases escriben el mismo ErrorResponse que el resto de la API.
 */
@Component
class RestAuthenticationEntryPoint(private val objectMapper: ObjectMapper) : AuthenticationEntryPoint {

    /** 401: no hay token, o el token es inválido / vencido / de una cuenta no activa. */
    override fun commence(request: HttpServletRequest, response: HttpServletResponse, ex: AuthenticationException) {
        val reason = request.getAttribute(JwtAuthenticationFilter.ERROR_ATTRIBUTE) as? String
            ?: "Falta el token de acceso. Envíe el header 'Authorization: Bearer <token>'"
        response.setHeader("WWW-Authenticate", "Bearer")
        writeError(objectMapper, response, request, HttpStatus.UNAUTHORIZED, reason)
    }
}

@Component
class RestAccessDeniedHandler(private val objectMapper: ObjectMapper) : AccessDeniedHandler {

    /** 403: el token es válido, pero el rol del usuario no tiene permiso para esta ruta. */
    override fun handle(request: HttpServletRequest, response: HttpServletResponse, ex: AccessDeniedException) {
        writeError(objectMapper, response, request, HttpStatus.FORBIDDEN, "No tiene permisos para realizar esta acción")
    }
}

private fun writeError(
    objectMapper: ObjectMapper,
    response: HttpServletResponse,
    request: HttpServletRequest,
    status: HttpStatus,
    message: String,
) {
    response.status = status.value()
    response.contentType = MediaType.APPLICATION_JSON_VALUE
    response.characterEncoding = Charsets.UTF_8.name()
    val body = ErrorResponse(
        status = status.value(),
        error = status.reasonPhrase,
        message = message,
        path = request.requestURI,
    )
    objectMapper.writeValue(response.outputStream, body)
}