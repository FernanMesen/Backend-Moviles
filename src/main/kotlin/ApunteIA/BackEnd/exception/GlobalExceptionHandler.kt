package ApunteIA.BackEnd.exception

import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.HttpMediaTypeNotSupportedException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.HandlerMethodValidationException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.servlet.resource.NoResourceFoundException

/**
 * Manejo global de errores de la API (Lab 6).
 *   400 → body inválido (@Valid), JSON mal formado, enum desconocido, tipo de parámetro incorrecto
 *   401 / 403 / 404 / 409 → ApiException lanzadas por los servicios
 *   405 → método no soportado · 415 → Content-Type no JSON · 500 → error no previsto
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(ApiException::class)
    fun handleApiException(ex: ApiException, request: HttpServletRequest) =
        build(ex.status, ex.message ?: ex.status.reasonPhrase, request)

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException, request: HttpServletRequest): ResponseEntity<ErrorResponse> {
        val fieldErrors = ex.bindingResult.fieldErrors
            .groupBy({ it.field }, { it.defaultMessage ?: "Valor inválido" })
            .mapValues { (_, messages) -> messages.joinToString("; ") }
        return build(HttpStatus.BAD_REQUEST, "La petición tiene campos inválidos", request, fieldErrors)
    }

    @ExceptionHandler(HandlerMethodValidationException::class)
    fun handleMethodValidation(ex: HandlerMethodValidationException, request: HttpServletRequest) =
        build(HttpStatus.BAD_REQUEST, "Parámetros inválidos", request)

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleUnreadable(ex: HttpMessageNotReadableException, request: HttpServletRequest) =
        build(HttpStatus.BAD_REQUEST, "El cuerpo de la petición no es un JSON válido o tiene valores no permitidos", request)

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatch(ex: MethodArgumentTypeMismatchException, request: HttpServletRequest) =
        build(HttpStatus.BAD_REQUEST, "El parámetro '${ex.name}' tiene un valor inválido: '${ex.value}'", request)

    @ExceptionHandler(MissingServletRequestParameterException::class)
    fun handleMissingParam(ex: MissingServletRequestParameterException, request: HttpServletRequest) =
        build(HttpStatus.BAD_REQUEST, "Falta el parámetro obligatorio '${ex.parameterName}'", request)

    @ExceptionHandler(DataIntegrityViolationException::class)
    fun handleDataIntegrity(ex: DataIntegrityViolationException, request: HttpServletRequest) =
        build(HttpStatus.CONFLICT, "La operación viola una restricción de datos (registro duplicado o relacionado)", request)

    @ExceptionHandler(NoResourceFoundException::class)
    fun handleNoRoute(ex: NoResourceFoundException, request: HttpServletRequest) =
        build(HttpStatus.NOT_FOUND, "La ruta ${request.requestURI} no existe", request)

    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    fun handleMethodNotAllowed(ex: HttpRequestMethodNotSupportedException, request: HttpServletRequest) =
        build(HttpStatus.METHOD_NOT_ALLOWED, "El método ${ex.method} no está permitido en esta ruta", request)

    @ExceptionHandler(HttpMediaTypeNotSupportedException::class)
    fun handleMediaType(ex: HttpMediaTypeNotSupportedException, request: HttpServletRequest) =
        build(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "El Content-Type debe ser application/json", request)

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(ex: Exception, request: HttpServletRequest): ResponseEntity<ErrorResponse> {
        log.error("Error no controlado en ${request.method} ${request.requestURI}", ex)
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Ocurrió un error inesperado en el servidor", request)
    }

    private fun build(
        status: HttpStatus,
        message: String,
        request: HttpServletRequest,
        fieldErrors: Map<String, String>? = null,
    ): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(status).body(
            ErrorResponse(
                status = status.value(),
                error = status.reasonPhrase,
                message = message,
                path = request.requestURI,
                fieldErrors = fieldErrors,
            )
        )
}