package ApunteIA.BackEnd.repository

import ApunteIA.BackEnd.entity.Assessment
import ApunteIA.BackEnd.entity.AssessmentAttempt
import ApunteIA.BackEnd.entity.Course
import ApunteIA.BackEnd.entity.Privilege
import ApunteIA.BackEnd.entity.QuestionData
import ApunteIA.BackEnd.entity.Role
import ApunteIA.BackEnd.entity.User
import ApunteIA.BackEnd.entity.UserAnswerData
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.dao.DataIntegrityViolationException
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Pruebas de persistencia de evaluaciones e intentos, incluido el almacenamiento JSON. */
@DataJpaTest
class AssessmentRepositoryTest @Autowired constructor(
    private val assessmentRepository: AssessmentRepository,
    private val attemptRepository: AssessmentAttemptRepository,
    private val courseRepository: CourseRepository,
    private val roleRepository: RoleRepository,
    private val privilegeRepository: PrivilegeRepository,
    private val userRepository: UserRepository,
    private val entityManager: EntityManager,
) {
    private lateinit var user: User
    private lateinit var course: Course
    private lateinit var assessment: Assessment

    @BeforeEach
    fun setUp() {
        val role = roleRepository.save(Role(Role.STUDENT, "Estudiante"))
        val privilege = privilegeRepository.save(Privilege(Privilege.BASIC, "Acceso básico"))
        user = userRepository.save(
            User(
                email = "estudiante@apunteia.com",
                fullName = "Estudiante de Prueba",
                role = role,
                privilege = privilege,
            )
        )
        course = courseRepository.save(Course(code = "EIF411", name = "Backend Móvil"))
        assessment = assessmentRepository.save(
            Assessment(
                title = "Introducción al Backend",
                description = "Conceptos base",
                durationMinutes = 15,
                course = course,
                questions = listOf(
                    QuestionData("¿Qué método HTTP crea un recurso?", listOf("GET", "DELETE", "POST"), 2),
                    QuestionData("¿Qué abstrae el patrón Repositorio?", listOf("El acceso a datos", "La interfaz"), 0),
                ),
            )
        )
    }

    private fun flushAndClear() {
        entityManager.flush()
        entityManager.clear()
    }

    private fun attempt(completedAt: String, score: Int = 50, assessment: Assessment = this.assessment) =
        AssessmentAttempt(
            assessment = assessment,
            user = user,
            scorePercent = score,
            completedAt = Instant.parse(completedAt),
            durationSeconds = 120L,
            userAnswers = listOf(
                UserAnswerData(0, 2, true),
                UserAnswerData(1, null, false),
            ),
        )

    @Test
    fun `guarda una evaluacion y conserva sus preguntas en JSON`() {
        flushAndClear()

        val found = assessmentRepository.findByTitle("Introducción al Backend")

        assertNotNull(found)
        assertNotNull(found.id)
        assertEquals(15, found.durationMinutes)
        assertEquals(2, found.questions.size)
        assertEquals("¿Qué método HTTP crea un recurso?", found.questions[0].text)
        assertEquals(listOf("GET", "DELETE", "POST"), found.questions[0].options)
        assertEquals(2, found.questions[0].correctOptionIndex)
        assertTrue(assessmentRepository.existsByTitle("Introducción al Backend"))
        assertFalse(assessmentRepository.existsByTitle("No existe"))
    }

    @Test
    fun `consulta las evaluaciones de un curso`() {
        val otherCourse = courseRepository.save(Course(code = "EIF412", name = "Bases de Datos"))
        assessmentRepository.save(Assessment(title = "Modelado relacional", course = otherCourse))
        flushAndClear()

        val found = assessmentRepository.findAllByCourse_Id(course.id!!)

        assertEquals(listOf("Introducción al Backend"), found.map { it.title })
    }

    @Test
    fun `rechaza dos evaluaciones con el mismo titulo`() {
        assertThrows<DataIntegrityViolationException> {
            assessmentRepository.saveAndFlush(Assessment(title = "Introducción al Backend"))
        }
    }

    @Test
    fun `guarda un intento y conserva sus respuestas en JSON`() {
        val saved = attemptRepository.save(attempt("2026-09-20T15:30:00Z"))
        flushAndClear()

        val found = attemptRepository.findById(saved.id!!).orElseThrow()

        assertEquals(user.id, found.user.id)
        assertEquals(assessment.id, found.assessment.id)
        assertEquals(50, found.scorePercent)
        assertEquals(120L, found.durationSeconds)
        assertEquals(Instant.parse("2026-09-20T15:30:00Z"), found.completedAt)
        assertEquals(2, found.userAnswers.size)
        assertTrue(found.userAnswers[0].correct)
        assertEquals(2, found.userAnswers[0].selectedOptionIndex)
        assertNull(found.userAnswers[1].selectedOptionIndex)
    }

    @Test
    fun `lista los intentos de un usuario del mas reciente al mas antiguo`() {
        attemptRepository.save(attempt("2026-09-20T15:30:00Z"))
        attemptRepository.save(attempt("2026-09-25T16:10:00Z"))
        attemptRepository.save(attempt("2026-09-22T14:00:00Z"))
        flushAndClear()

        val found = attemptRepository.findAllByUser_IdOrderByCompletedAtDesc(user.id!!)

        assertEquals(
            listOf(
                Instant.parse("2026-09-25T16:10:00Z"),
                Instant.parse("2026-09-22T14:00:00Z"),
                Instant.parse("2026-09-20T15:30:00Z"),
            ),
            found.map { it.completedAt },
        )
    }

    @Test
    fun `filtra los intentos por evaluacion y usuario`() {
        val other = assessmentRepository.save(Assessment(title = "Modelado relacional", course = course))
        attemptRepository.save(attempt("2026-09-20T15:30:00Z"))
        attemptRepository.save(attempt("2026-09-21T15:30:00Z", assessment = other))
        flushAndClear()

        val found = attemptRepository.findAllByAssessment_IdAndUser_Id(assessment.id!!, user.id!!)

        assertEquals(1, found.size)
        assertEquals(assessment.id, found.first().assessment.id)
    }

    @Test
    fun `verifica si un intento ya existe por usuario evaluacion y fecha`() {
        attemptRepository.save(attempt("2026-09-20T15:30:00Z"))
        flushAndClear()

        assertTrue(
            attemptRepository.existsByUser_IdAndAssessment_IdAndCompletedAt(
                user.id!!, assessment.id!!, Instant.parse("2026-09-20T15:30:00Z"),
            )
        )
        assertFalse(
            attemptRepository.existsByUser_IdAndAssessment_IdAndCompletedAt(
                user.id!!, assessment.id!!, Instant.parse("2026-09-21T15:30:00Z"),
            )
        )
    }
}