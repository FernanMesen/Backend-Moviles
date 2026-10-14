package ApunteIA.BackEnd.mapper

import ApunteIA.BackEnd.dto.enrollment.EnrollmentResponse
import ApunteIA.BackEnd.entity.Enrollment
import org.springframework.stereotype.Component

/** Se utiliza dentro de un servicio @Transactional para acceder a las relaciones LAZY. */
@Component
class EnrollmentMapper {
    fun toResponse(enrollment: Enrollment) = EnrollmentResponse(
        id = requireNotNull(enrollment.id) { "La inscripción no ha sido guardada" },
        userId = requireNotNull(enrollment.user.id) { "El usuario de la inscripción no tiene id" },
        userFullName = enrollment.user.fullName,
        userEmail = enrollment.user.email,
        courseId = requireNotNull(enrollment.course.id) { "El curso de la inscripción no tiene id" },
        courseCode = enrollment.course.code,
        courseName = enrollment.course.name,
        enrolledAt = enrollment.enrolledAt,
    )
}
