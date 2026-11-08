package ApunteIA.BackEnd.seed

import ApunteIA.BackEnd.entity.Course
import ApunteIA.BackEnd.entity.Enrollment
import ApunteIA.BackEnd.entity.SecurityLog
import ApunteIA.BackEnd.entity.User
import ApunteIA.BackEnd.repository.CourseRepository
import ApunteIA.BackEnd.repository.EnrollmentRepository
import ApunteIA.BackEnd.repository.SecurityLogRepository
import ApunteIA.BackEnd.repository.UserRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/** Datos de ejemplo para cursos, inscripciones y auditoría. Se puede ejecutar varias veces. */
@Component
class AcademicSeeder(
    private val courseRepository: CourseRepository,
    private val enrollmentRepository: EnrollmentRepository,
    private val securityLogRepository: SecurityLogRepository,
    private val userRepository: UserRepository,
) {
    @Transactional
    fun seed() {
        if (listOf("demo@apunteia.com", "profe@apunteia.com", "bloqueado@apunteia.com", "google.user@gmail.com")
                .any { !userRepository.existsByEmail(it) }) return
        val demo = user("demo@apunteia.com")
        val professor = user("profe@apunteia.com")
        val blocked = user("bloqueado@apunteia.com")
        val google = user("google.user@gmail.com")

        val backend = course("EIF411", "Backend para plataformas móviles", "Persistencia y servicios backend")
        val databases = course("EIF412", "Bases de datos", "Modelado relacional y consultas")
        val distributed = course("EIF413", "Programación distribuida", "Conceptos y sistemas distribuidos")

        enroll(demo, backend)
        enroll(demo, databases)
        enroll(google, backend)
        enroll(professor, distributed)

        log("LOGIN", demo, "Inicio de sesión de prueba", "192.0.2.10")
        log("LOGIN_FAILED", blocked, "Intento de acceso con cuenta bloqueada", "192.0.2.11")
        log("COURSE_CREATED", professor, "Curso EIF413 creado para datos de prueba", "192.0.2.12")
    }

    private fun user(email: String): User =
        userRepository.findByEmail(email)
            ?: error("No existe el usuario requerido para los datos académicos: $email")

    private fun course(code: String, name: String, description: String): Course =
        courseRepository.findByCode(code) ?: courseRepository.save(
            Course(code = code, name = name, description = description)
        )

    private fun enroll(user: User, course: Course) {
        if (!enrollmentRepository.existsByUser_IdAndCourse_Id(user.id!!, course.id!!)) {
            enrollmentRepository.save(Enrollment(user = user, course = course))
        }
    }

    private fun log(eventType: String, user: User, details: String, ipAddress: String) {
        val alreadySeeded = securityLogRepository.findAllByEventTypeOrderByOccurredAtDesc(eventType)
            .any { it.user?.id == user.id && it.details == details }
        if (!alreadySeeded) {
            securityLogRepository.save(
                SecurityLog(eventType = eventType, user = user, details = details, ipAddress = ipAddress)
            )
        }
    }
}
