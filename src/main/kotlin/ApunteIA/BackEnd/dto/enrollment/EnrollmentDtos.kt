package ApunteIA.BackEnd.dto.enrollment

import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import java.time.LocalDateTime

data class CreateEnrollmentRequest(
    @field:NotNull(message = "El usuario es obligatorio")
    @field:Positive(message = "El id del usuario debe ser positivo")
    val userId: Long? = null,

    @field:NotNull(message = "El curso es obligatorio")
    @field:Positive(message = "El id del curso debe ser positivo")
    val courseId: Long? = null,
)

/** Información básica de la inscripción, sin entidades ni credenciales del usuario. */
data class EnrollmentResponse(
    val id: Long,
    val userId: Long,
    val userFullName: String,
    val userEmail: String,
    val courseId: Long,
    val courseCode: String,
    val courseName: String,
    val enrolledAt: LocalDateTime?,
)
