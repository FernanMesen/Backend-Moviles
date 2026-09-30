package ApunteIA.BackEnd.seed

import ApunteIA.BackEnd.entity.Assessment
import ApunteIA.BackEnd.entity.AssessmentAttempt
import ApunteIA.BackEnd.entity.Course
import ApunteIA.BackEnd.entity.QuestionData
import ApunteIA.BackEnd.entity.User
import ApunteIA.BackEnd.entity.UserAnswerData
import ApunteIA.BackEnd.repository.AssessmentAttemptRepository
import ApunteIA.BackEnd.repository.AssessmentRepository
import ApunteIA.BackEnd.repository.CourseRepository
import ApunteIA.BackEnd.repository.UserRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/** Datos de ejemplo para evaluaciones e intentos. Se puede ejecutar varias veces. */
@Component
class AssessmentSeeder(
    private val assessmentRepository: AssessmentRepository,
    private val attemptRepository: AssessmentAttemptRepository,
    private val courseRepository: CourseRepository,
    private val userRepository: UserRepository,
) {
    @Transactional
    fun seed() {
        val demo = user("demo@apunteia.com")
        val google = user("google.user@gmail.com")

        val backend = course("EIF411")
        val databases = course("EIF412")
        val distributed = course("EIF413")

        val backendBasics = assessment(
            backend, "Introducción al Backend", "Conceptos base de servidores y APIs", 15,
            listOf(
                QuestionData(
                    "¿Qué responsabilidad tiene principalmente el backend?",
                    listOf("Renderizar la interfaz", "Procesar la lógica de negocio y persistir datos", "Compilar la app móvil", "Diseñar los íconos"),
                    1,
                ),
                QuestionData(
                    "¿Qué método HTTP se usa normalmente para crear un recurso?",
                    listOf("GET", "DELETE", "POST", "HEAD"),
                    2,
                ),
                QuestionData(
                    "¿Qué representa el patrón Repositorio?",
                    listOf("Una capa que abstrae el acceso a datos", "Un componente visual", "Un protocolo de red", "Un sistema de caché"),
                    0,
                ),
            ),
        )

        val relationalModel = assessment(
            databases, "Modelado relacional", "Claves primarias, foráneas y relaciones", 20,
            listOf(
                QuestionData(
                    "¿Qué garantiza una clave primaria?",
                    listOf("Que el valor puede repetirse", "Que cada fila se identifica de forma única", "Que la tabla no tiene relaciones", "Que el valor es opcional"),
                    1,
                ),
                QuestionData(
                    "¿Para qué sirve una clave foránea?",
                    listOf("Ordenar los resultados", "Cifrar la columna", "Relacionar una tabla con otra", "Crear una vista"),
                    2,
                ),
                QuestionData(
                    "¿Qué relación modela a un curso con muchas inscripciones?",
                    listOf("Uno a muchos", "Uno a uno", "Muchos a muchos sin tabla intermedia", "Ninguna"),
                    0,
                ),
            ),
        )

        val distributedBasics = assessment(
            distributed, "Programación distribuida", "Conceptos y ejemplos básicos", 15,
            listOf(
                QuestionData(
                    "¿Qué caracteriza a un sistema distribuido?",
                    listOf("Un único nodo de procesamiento", "Varios nodos que se coordinan mediante mensajes", "Que no usa red", "Que no tiene estado"),
                    1,
                ),
                QuestionData(
                    "¿Qué modelo de comunicación usa una petición y espera una respuesta?",
                    listOf("Cliente-servidor", "Broadcast", "Gossip", "Multicast"),
                    0,
                ),
            ),
        )

        attempt(demo, backendBasics, listOf(1, 2, 0), 420, Instant.parse("2026-09-20T15:30:00Z"))
        attempt(demo, backendBasics, listOf(1, 2, 2), 380, Instant.parse("2026-09-25T16:10:00Z"))
        attempt(demo, relationalModel, listOf(1, 2, 0), 610, Instant.parse("2026-09-22T14:00:00Z"))
        attempt(google, backendBasics, listOf(0, 2, 0), 500, Instant.parse("2026-09-24T18:45:00Z"))
        attempt(google, distributedBasics, listOf(1, null), 300, Instant.parse("2026-09-26T13:20:00Z"))
    }

    private fun user(email: String): User =
        userRepository.findByEmail(email)
            ?: error("No existe el usuario requerido para las evaluaciones: $email")

    private fun course(code: String): Course =
        courseRepository.findByCode(code)
            ?: error("No existe el curso requerido para las evaluaciones: $code")

    private fun assessment(
        course: Course,
        title: String,
        description: String,
        durationMinutes: Int,
        questions: List<QuestionData>,
    ): Assessment =
        assessmentRepository.findByTitle(title) ?: assessmentRepository.save(
            Assessment(
                title = title,
                description = description,
                durationMinutes = durationMinutes,
                course = course,
                questions = questions,
            )
        )

    private fun attempt(
        user: User,
        assessment: Assessment,
        selected: List<Int?>,
        durationSeconds: Long,
        completedAt: Instant,
    ) {
        if (attemptRepository.existsByUser_IdAndAssessment_IdAndCompletedAt(user.id!!, assessment.id!!, completedAt)) return

        val answers = assessment.questions.mapIndexed { index, question ->
            UserAnswerData(
                questionIndex = index,
                selectedOptionIndex = selected[index],
                correct = selected[index] == question.correctOptionIndex,
            )
        }
        attemptRepository.save(
            AssessmentAttempt(
                assessment = assessment,
                user = user,
                scorePercent = answers.count { it.correct } * 100 / assessment.questions.size,
                completedAt = completedAt,
                durationSeconds = durationSeconds,
                userAnswers = answers,
            )
        )
    }
}