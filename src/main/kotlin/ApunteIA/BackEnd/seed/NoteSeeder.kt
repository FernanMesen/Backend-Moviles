package ApunteIA.BackEnd.seed

import ApunteIA.BackEnd.entity.Course
import ApunteIA.BackEnd.entity.Note
import ApunteIA.BackEnd.entity.ProcessingStatus
import ApunteIA.BackEnd.entity.User
import ApunteIA.BackEnd.repository.CourseRepository
import ApunteIA.BackEnd.repository.NoteRepository
import ApunteIA.BackEnd.repository.UserRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/** Notas de ejemplo para usuarios y cursos existentes. Se puede ejecutar varias veces. */
@Component
class NoteSeeder(
    private val noteRepository: NoteRepository,
    private val userRepository: UserRepository,
    private val courseRepository: CourseRepository,
) {
    @Transactional
    fun seed() {
        if (listOf("demo@apunteia.com", "google.user@gmail.com", "profe@apunteia.com")
                .any { !userRepository.existsByEmail(it) }) return
        val demo = user("demo@apunteia.com")
        val google = user("google.user@gmail.com")
        val professor = user("profe@apunteia.com")
        val backend = course("EIF411")
        val databases = course("EIF412")
        val distributed = course("EIF413")

        note(
            demo, backend, "Entidades y relaciones JPA",
            "Una entidad representa una tabla. ManyToOne relaciona varias notas con un usuario o curso. " +
                "Las claves foráneas conservan la integridad de las relaciones.",
            ProcessingStatus.COMPLETED,
        )
        note(
            demo, databases, "Normalización de bases de datos",
            "Apuntes escaneados sobre dependencias funcionales y formas normales, pendientes de procesamiento.",
            ProcessingStatus.PENDING,
        )
        note(
            google, backend, "Ciclo de vida de una aplicación móvil",
            "Apuntes escaneados sobre creación, pausa y reanudación de actividades; extracción de texto en proceso.",
            ProcessingStatus.PROCESSING,
        )
        note(
            professor, distributed, "Consenso en sistemas distribuidos",
            "Apuntes sobre consenso y tolerancia a fallos. El escaneo está borroso y no se pudo completar la extracción.",
            ProcessingStatus.FAILED,
        )
    }

    private fun user(email: String): User =
        userRepository.findByEmail(email)
            ?: error("No existe el usuario requerido para las notas de ejemplo: $email")

    private fun course(code: String): Course =
        courseRepository.findByCode(code)
            ?: error("No existe el curso requerido para las notas de ejemplo: $code")

    private fun note(user: User, course: Course, title: String, content: String, status: ProcessingStatus) {
        // La identidad de cada ejemplo no depende de su estado ni de su contenido editable.
        val alreadySeeded = noteRepository.findAllByUser_IdAndCourse_Id(user.id!!, course.id!!)
            .any { it.title == title }
        if (!alreadySeeded) {
            noteRepository.save(
                Note(title = title, content = content, user = user, course = course, processingStatus = status)
            )
        }
    }
}
