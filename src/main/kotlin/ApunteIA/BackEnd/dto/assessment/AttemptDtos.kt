package ApunteIA.BackEnd.dto.attempt

import ApunteIA.BackEnd.entity.AttemptStatus
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import java.math.BigDecimal
import java.time.Instant

data class StartAttemptRequest(
    @field:NotNull(message = "El usuario es obligatorio")
    @field:Positive(message = "El id del usuario debe ser positivo")
    val userId: Long? = null,
)

/** Opción elegida por pregunta, en el mismo orden de la evaluación; null si quedó sin responder. */
data class SubmitAttemptRequest(
    @field:NotNull(message = "Las respuestas son obligatorias")
    val answers: List<Int?>? = null,
)

data class AttemptAnswerResponse(
    val questionIndex: Int,
    val selectedOptionIndex: Int?,
    val correct: Boolean,
    /** Opción correcta de la pregunta. Solo se envía cuando el intento ya fue entregado (null mientras está en progreso). */
    val correctOptionIndex: Int? = null,
)

data class AttemptResponse(
    val id: Long,
    val assessmentId: Long,
    val assessmentTitle: String,
    val userId: Long,
    val status: AttemptStatus,
    val score: BigDecimal?,
    val startedAt: Instant,
    val completedAt: Instant?,
    val durationSeconds: Long,
    val answers: List<AttemptAnswerResponse>,
)