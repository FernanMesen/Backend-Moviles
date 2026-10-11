package ApunteIA.BackEnd.exception

import com.fasterxml.jackson.annotation.JsonInclude
import java.time.LocalDateTime

/** Formato común de error de TODA la API. `message` es el mismo campo que ya lee el FrontEnd. */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ErrorResponse(
    val status: Int,
    val error: String,
    val message: String,
    val path: String,
    val fieldErrors: Map<String, String>? = null,
    val timestamp: LocalDateTime = LocalDateTime.now(),
)