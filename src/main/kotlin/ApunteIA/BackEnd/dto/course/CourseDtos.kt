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
)

data class CourseResponse(
    val id: Long,
    val code: String,
    val name: String,
    val description: String?,
)
