package ApunteIA.BackEnd.dto.course

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class CreateCourseRequest(
    @field:NotBlank(message = "El código del curso es obligatorio")
    @field:Size(max = 50, message = "El código del curso no puede tener más de 50 caracteres")
    val code: String = "",

    @field:NotBlank(message = "El nombre del curso es obligatorio")
    @field:Size(max = 150, message = "El nombre del curso no puede tener más de 150 caracteres")
    val name: String = "",

    @field:Size(max = 1000, message = "La descripción del curso no puede tener más de 1000 caracteres")
    val description: String? = null,

    @field:Size(max = 5000, message = "El plan de estudios no puede tener más de 5000 caracteres")
    val syllabus: String? = null,
)

data class UpdateCourseRequest(
    @field:NotBlank(message = "El código del curso es obligatorio")
    @field:Size(max = 50, message = "El código del curso no puede tener más de 50 caracteres")
    val code: String = "",

    @field:NotBlank(message = "El nombre del curso es obligatorio")
    @field:Size(max = 150, message = "El nombre del curso no puede tener más de 150 caracteres")
    val name: String = "",

    @field:Size(max = 1000, message = "La descripción del curso no puede tener más de 1000 caracteres")
    val description: String? = null,

    /** Si se omite, se conserva el plan actual; si se envía vacío, se borra. */
    @field:Size(max = 5000, message = "El plan de estudios no puede tener más de 5000 caracteres")
    val syllabus: String? = null,
)

data class CourseResponse(
    val id: Long,
    val code: String,
    val name: String,
    val description: String?,
    val syllabus: String? = null,
    val createdById: Long? = null,
    val createdByName: String? = null,
    val studentCount: Int = 0,
)
