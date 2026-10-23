package ApunteIA.BackEnd.exception

import org.springframework.http.HttpStatus

/**
 * Errores de negocio que lanzan los servicios.
 * Cada uno ya sabe qué código HTTP le corresponde; el GlobalExceptionHandler
 * solo los convierte en un ErrorResponse.
 *
 * Uso desde cualquier servicio del equipo:
 *   throw NotFoundException("Curso con id $id no existe")
 *   throw ConflictException("Ya existe un curso con el código $code")
 */
abstract class ApiException(
    val status: HttpStatus,
    message: String,
) : RuntimeException(message)

/** 400 — la petición es válida en formato, pero rompe una regla de negocio. */
class BadRequestException(message: String) : ApiException(HttpStatus.BAD_REQUEST, message)

/** 401 — credenciales inválidas o cuenta inexistente. */
class UnauthorizedException(message: String) : ApiException(HttpStatus.UNAUTHORIZED, message)

/** 403 — la cuenta existe y las credenciales son correctas, pero no puede entrar (p. ej. BLOCKED). */
class ForbiddenException(message: String) : ApiException(HttpStatus.FORBIDDEN, message)

/** 404 — el recurso pedido no existe. */
class NotFoundException(message: String) : ApiException(HttpStatus.NOT_FOUND, message)

/** 409 — choca con el estado actual de los datos (email duplicado, transición de estado inválida...). */
class ConflictException(message: String) : ApiException(HttpStatus.CONFLICT, message)

/** 502 — el proveedor externo de IA no respondió correctamente. */
class AiProviderException(message: String, cause: Throwable? = null) :
    ApiException(HttpStatus.BAD_GATEWAY, message) {
    init {
        if (cause != null) initCause(cause)
    }
}
