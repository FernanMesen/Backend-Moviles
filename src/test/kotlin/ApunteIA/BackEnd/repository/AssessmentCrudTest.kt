package ApunteIA.BackEnd.repository

import ApunteIA.BackEnd.entity.Assessment
import ApunteIA.BackEnd.entity.AssessmentAttempt
import ApunteIA.BackEnd.entity.AssessmentDifficulty
import ApunteIA.BackEnd.entity.AssessmentType
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
import java.math.BigDecimal
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Pruebas de inserción, búsqueda y actualización de Assessment y AssessmentAttempt. */
@DataJpaTest
class AssessmentCrudTest @Autowired constructor(
    private val assessmentRepository: AssessmentRepository,
    private val attemptRepository: AssessmentAttemptRepository,
    private val courseRepository: CourseRepository,
    private val roleRepository: RoleRepository,
    private val privilegeRepository: PrivilegeRepository,
    private val userRepository: UserRepository,
    private val entityManager: EntityManager,
) {
    private lateinit var ana: User
    private lateinit var luis: User
    private lateinit var backend: Course
    private lateinit var databases: Course
    private lateinit var quiz: Assessment

    private val questions = listOf(
        QuestionData("¿Qué método HTTP crea un recurso?", listOf("GET", "POST", "DELETE"), 1),
        QuestionData("¿Qué abstrae el patrón Repositorio?", listOf("El acceso a datos", "La interfaz"), 0),
    )

    @BeforeEach
    fun setUp() {
        val role = roleRepository.save(Role(Role.STUDENT, "Estudiante"))
        val privilege = privilegeRepository.save(Privilege(Privilege.BASIC, "Acceso básico"))
        ana = userRepository.save(User(email = "ana@apunteia.com", fullName = "Ana", role = role, privilege = privilege))
        luis = userRepository.save(User(email = "luis@apunteia.com", fullName = "Luis", role = role, privilege = privilege))
        backend = courseRepository.save(Course(code = "EIF411", name = "Backend Móvil"))
        databases = courseRepository.save(Course(code = "EIF412", name = "Bases de datos"))
        quiz = assessmentRepository.save(
            Assessment(title = "Quiz Backend", description = "Conceptos base", durationMinutes = 10, course = backend, questions = questions)
        )
    }

    private fun flushAndClear() {
        entityManager.flush()
        entityManager.clear()
    }

    private fun attempt(user: User, completedAt: String, score: BigDecimal = BigDecimal("50.00")) =
        AssessmentAttempt(
            assessment = quiz,
            user = user,
            score = score,
            startedAt = Instant.parse(completedAt).minusSeconds(90L),
            completedAt = Instant.parse(completedAt),
            durationSeconds = 90L,
            userAnswers = listOf(UserAnswerData(0, 1, true), UserAnswerData(1, 1, false)),
        )

    // ---------- ASSESSMENT ----------

    @Test
    fun `insertar una evaluacion guarda todos sus campos`() {
        flushAndClear()

        val found = assessmentRepository.findById(quiz.id!!).orElseThrow()
        assertEquals("Quiz Backend", found.title)
        assertEquals("Conceptos base", found.description)
        assertEquals(10, found.durationMinutes)
        assertEquals(AssessmentType.QUIZ, found.type)
        assertEquals(AssessmentDifficulty.MEDIUM, found.difficulty)
        assertEquals("EIF411", found.course?.code)
        assertEquals(questions, found.questions)
    }

    @Test
    fun `insertar una evaluacion guarda tipo, dificultad y creador`() {
        val saved = assessmentRepository.save(
            Assessment(
                title = "Examen BD",
                type = AssessmentType.EXAM,
                difficulty = AssessmentDifficulty.HARD,
                createdBy = ana,
                questions = questions,
            )
        )
        flushAndClear()

        val found = assessmentRepository.findById(saved.id!!).orElseThrow()
        assertEquals(AssessmentType.EXAM, found.type)
        assertEquals(AssessmentDifficulty.HARD, found.difficulty)
        assertEquals("ana@apunteia.com", found.createdBy?.email)
    }

    @Test
    fun `insertar una evaluacion sin curso`() {
        val saved = assessmentRepository.save(Assessment(title = "Práctica libre", questions = questions))
        flushAndClear()

        assertNull(assessmentRepository.findById(saved.id!!).orElseThrow().course)
    }

    @Test
    fun `listar y contar evaluaciones`() {
        assessmentRepository.save(Assessment(title = "Quiz BD", course = databases, questions = questions))
        flushAndClear()

        assertEquals(2, assessmentRepository.count())
        assertEquals(setOf("Quiz Backend", "Quiz BD"), assessmentRepository.findAll().map { it.title }.toSet())
    }

    @Test
    fun `buscar una evaluacion inexistente`() {
        assertNull(assessmentRepository.findByTitle("No existe"))
        assertFalse(assessmentRepository.existsByTitle("No existe"))
        assertTrue(assessmentRepository.findById(999_999L).isEmpty)
        assertTrue(assessmentRepository.findAllByCourse_Id(databases.id!!).isEmpty())
    }

    @Test
    fun `actualizar titulo, duracion y preguntas de una evaluacion`() {
        flushAndClear()

        val loaded = assessmentRepository.findByTitle("Quiz Backend")!!
        loaded.title = "Quiz Backend v2"
        loaded.durationMinutes = 20
        loaded.questions = questions + QuestionData("¿Qué hace @Entity?", listOf("Marca una tabla", "Crea un endpoint"), 0)
        assessmentRepository.save(loaded)
        flushAndClear()

        assertNull(assessmentRepository.findByTitle("Quiz Backend"))
        val found = assessmentRepository.findByTitle("Quiz Backend v2")!!
        assertEquals(20, found.durationMinutes)
        assertEquals(3, found.questions.size)
        assertEquals("¿Qué hace @Entity?", found.questions.last().text)
    }

    @Test
    fun `mover una evaluacion a otro curso`() {
        flushAndClear()

        val loaded = assessmentRepository.findById(quiz.id!!).orElseThrow()
        loaded.course = courseRepository.findByCode("EIF412")
        assessmentRepository.save(loaded)
        flushAndClear()

        assertTrue(assessmentRepository.findAllByCourse_Id(backend.id!!).isEmpty())
        assertEquals(listOf(quiz.id), assessmentRepository.findAllByCourse_Id(databases.id!!).map { it.id })
    }

    // ---------- ATTEMPT ----------

    @Test
    fun `insertar un intento guarda todos sus campos`() {
        val saved = attemptRepository.save(attempt(ana, "2026-09-20T10:00:00Z", score = BigDecimal("50.00")))
        flushAndClear()

        val found = attemptRepository.findById(saved.id!!).orElseThrow()
        assertEquals(BigDecimal("50.00"), found.score)
        assertEquals(90L, found.durationSeconds)
        assertEquals(Instant.parse("2026-09-20T10:00:00Z"), found.completedAt)
        assertEquals(Instant.parse("2026-09-20T09:58:30Z"), found.startedAt)
        assertEquals("ana@apunteia.com", found.user.email)
        assertEquals("Quiz Backend", found.assessment.title)
        assertEquals(2, found.userAnswers.size)
        assertTrue(found.userAnswers.first().correct)
    }

    @Test
    fun `insertar varios intentos y contarlos`() {
        attemptRepository.saveAll(
            listOf(
                attempt(ana, "2026-09-20T10:00:00Z"),
                attempt(ana, "2026-09-21T10:00:00Z"),
                attempt(luis, "2026-09-22T10:00:00Z"),
            )
        )
        flushAndClear()

        assertEquals(3, attemptRepository.count())
    }

    @Test
    fun `filtrar intentos por evaluacion y usuario no mezcla usuarios`() {
        attemptRepository.saveAll(
            listOf(attempt(ana, "2026-09-20T10:00:00Z"), attempt(luis, "2026-09-21T10:00:00Z"))
        )
        flushAndClear()

        val anaAttempts = attemptRepository.findAllByAssessment_IdAndUser_Id(quiz.id!!, ana.id!!)
        assertEquals(1, anaAttempts.size)
        assertEquals("ana@apunteia.com", anaAttempts.first().user.email)
    }

    @Test
    fun `un usuario sin intentos devuelve lista vacia`() {
        attemptRepository.save(attempt(ana, "2026-09-20T10:00:00Z"))
        flushAndClear()

        assertTrue(attemptRepository.findAllByUser_IdOrderByCompletedAtDesc(luis.id!!).isEmpty())
    }

    @Test
    fun `actualizar el puntaje y las respuestas de un intento`() {
        val saved = attemptRepository.save(attempt(ana, "2026-09-20T10:00:00Z", score = BigDecimal("50.00")))
        flushAndClear()

        val loaded = attemptRepository.findById(saved.id!!).orElseThrow()
        loaded.score = BigDecimal("100.00")
        loaded.userAnswers = listOf(UserAnswerData(0, 1, true), UserAnswerData(1, 0, true))
        attemptRepository.save(loaded)
        flushAndClear()

        val found = attemptRepository.findById(saved.id!!).orElseThrow()
        assertEquals(BigDecimal("100.00"), found.score)
        assertTrue(found.userAnswers.all { it.correct })
    }

    @Test
    fun `no permite un intento de una evaluacion que no existe`() {
        val unsaved = Assessment(title = "Fantasma", questions = questions)
        assertThrows<Exception> {
            attemptRepository.saveAndFlush(
                AssessmentAttempt(assessment = unsaved, user = ana)
            )
        }
    }

    @Test
    fun `eliminar un intento no borra la evaluacion`() {
        val saved = attemptRepository.save(attempt(ana, "2026-09-20T10:00:00Z"))
        flushAndClear()

        attemptRepository.deleteById(saved.id!!)
        flushAndClear()

        assertFalse(attemptRepository.existsById(saved.id!!))
        assertNotNull(assessmentRepository.findByTitle("Quiz Backend"))
    }
}