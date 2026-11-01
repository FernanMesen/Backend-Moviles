package ApunteIA.BackEnd.dto.assessment

import ApunteIA.BackEnd.entity.AssessmentDifficulty
import ApunteIA.BackEnd.entity.AssessmentType
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min

data class CreateQuestionRequest(
    @field:NotBlank(message = "El enunciado de la pregunta es obligatorio")
    @field:Size(max = 1000, message = "El enunciado no puede tener más de 1000 caracteres")
    val text: String = "",

    @field:Size(min = 2, max = 10, message = "Cada pregunta debe tener entre 2 y 10 opciones")
    val options: List<String> = emptyList(),

    @field:NotNull(message = "El índice de la opción correcta es obligatorio")
    @field:PositiveOrZero(message = "El índice de la opción correcta no puede ser negativo")
    val correctOptionIndex: Int? = null,
)

data class CreateAssessmentRequest(
    @field:NotBlank(message = "El título de la evaluación es obligatorio")
    @field:Size(max = 200, message = "El título no puede tener más de 200 caracteres")
    val title: String = "",

    @field:Size(max = 1000, message = "La descripción no puede tener más de 1000 caracteres")
    val description: String? = null,

    val type: AssessmentType = AssessmentType.QUIZ,

    val difficulty: AssessmentDifficulty = AssessmentDifficulty.MEDIUM,

    @field:PositiveOrZero(message = "La duración no puede ser negativa")
    val durationMinutes: Int = 0,

    @field:Positive(message = "El id del usuario creador debe ser positivo")
    val createdByUserId: Long? = null,

    @field:NotEmpty(message = "La evaluación debe tener al menos una pregunta")
    @field:Valid
    val questions: List<CreateQuestionRequest> = emptyList(),
)

data class QuestionResponse(
    val index: Int,
    val text: String,
    val options: List<String>,
)

/** Información de la evaluación sin la opción correcta de cada pregunta. */
data class AssessmentResponse(
    val id: Long,
    val title: String,
    val description: String?,
    val type: AssessmentType,
    val difficulty: AssessmentDifficulty,
    val durationMinutes: Int,
    val courseId: Long?,
    val courseName: String?,
    val createdByUserId: Long?,
    val totalQuestions: Int,
    val questions: List<QuestionResponse>,
)

data class GenerateAssessmentRequest(
    @field:NotNull(message = "La cantidad de preguntas es obligatoria")
    @field:Min(value = 1, message = "Se debe generar al menos 1 pregunta")
    @field:Max(value = 20, message = "No se pueden generar más de 20 preguntas")
    val questionCount: Int? = null,

    val difficulty: AssessmentDifficulty = AssessmentDifficulty.MEDIUM,

    val language: String? = null,
)
