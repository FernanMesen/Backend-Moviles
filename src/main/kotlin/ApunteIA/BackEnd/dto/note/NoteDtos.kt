package ApunteIA.BackEnd.dto.note

import ApunteIA.BackEnd.entity.ProcessingStatus
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import java.time.LocalDateTime

/** Datos necesarios para crear una nota. El estado inicial siempre es PENDING. */
data class CreateNoteRequest(
    @field:NotBlank(message = "El título es obligatorio")
    val title: String = "",

    @field:NotBlank(message = "El contenido es obligatorio")
    val content: String = "",

    @field:NotNull(message = "El usuario es obligatorio")
    @field:Positive(message = "El id del usuario debe ser positivo")
    val userId: Long? = null,

    @field:NotNull(message = "El curso es obligatorio")
    @field:Positive(message = "El id del curso debe ser positivo")
    val courseId: Long? = null,
)

/** Datos editables de una nota. El estado se controla por separado. */
data class UpdateNoteRequest(
    @field:NotBlank(message = "El título es obligatorio")
    val title: String = "",

    @field:NotBlank(message = "El contenido es obligatorio")
    val content: String = "",

    @field:NotNull(message = "El usuario es obligatorio")
    @field:Positive(message = "El id del usuario debe ser positivo")
    val userId: Long? = null,

    @field:NotNull(message = "El curso es obligatorio")
    @field:Positive(message = "El id del curso debe ser positivo")
    val courseId: Long? = null,
)

data class UpdateNoteStatusRequest(
    @field:NotNull(message = "El estado es obligatorio")
    val status: ProcessingStatus? = null,
)

data class NoteResponse(
    val id: Long,
    val title: String,
    val content: String,
    val userId: Long,
    val courseId: Long,
    val courseCode: String,
    val processingStatus: ProcessingStatus,
    val createdAt: LocalDateTime?,
    val updatedAt: LocalDateTime?,
)
