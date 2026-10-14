package ApunteIA.BackEnd.service

import ApunteIA.BackEnd.dto.assessment.AssessmentResponse
import ApunteIA.BackEnd.dto.assessment.CreateAssessmentRequest
import ApunteIA.BackEnd.dto.assessment.CreateQuestionRequest
import ApunteIA.BackEnd.dto.attempt.StartAttemptRequest
import ApunteIA.BackEnd.dto.attempt.SubmitAttemptRequest
import ApunteIA.BackEnd.entity.AssessmentAttempt
import ApunteIA.BackEnd.entity.AttemptStatus
import ApunteIA.BackEnd.entity.Course
import ApunteIA.BackEnd.entity.Privilege
import ApunteIA.BackEnd.entity.Role
import ApunteIA.BackEnd.entity.User
import ApunteIA.BackEnd.exception.BadRequestException
import ApunteIA.BackEnd.exception.ConflictException
import ApunteIA.BackEnd.exception.NotFoundException
import ApunteIA.BackEnd.mapper.AssessmentMapper
import ApunteIA.BackEnd.mapper.AttemptMapper
import ApunteIA.BackEnd.repository.AssessmentAttemptRepository
import ApunteIA.BackEnd.repository.AssessmentRepository
import ApunteIA.BackEnd.repository.CourseRepository
import ApunteIA.BackEnd.repository.PrivilegeRepository
import ApunteIA.BackEnd.repository.RoleRepository
import ApunteIA.BackEnd.repository.UserRepository
import ApunteIA.BackEnd.service.impl.AssessmentServiceImpl
import ApunteIA.BackEnd.service.impl.AttemptServiceImpl
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.Import
import java.math.BigDecimal
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@DataJpaTest
@Import(AssessmentServiceImpl::class, AttemptServiceImpl::class, AssessmentMapper::class, AttemptMapper::class)
class AssessmentServicesTest @Autowired constructor(
    private val assessments: AssessmentService,
    private val attempts: AttemptService,
    private val courseRepository: CourseRepository,
    private val userRepository: UserRepository,
    private val roleRepository: RoleRepository,
    private val privilegeRepository: PrivilegeRepository,
    private val assessmentRepository: AssessmentRepository,
    private val attemptRepository: AssessmentAttemptRepository,
    private val entityManager: EntityManager,
) {
    private lateinit var user: User
    private lateinit var otherUser: User
    private lateinit var course: Course
    private lateinit var assessment: AssessmentResponse
    private val missingId = Long.MAX_VALUE

    @BeforeEach
    fun setUp() {
        val role = roleRepository.save(Role(Role.STUDENT))
        val privilege = privilegeRepository.save(Privilege(Privilege.BASIC))
        user = userRepository.saveAndFlush(User("ana@test.com", "Ana", role, privilege))
        otherUser = userRepository.saveAndFlush(User("luis@test.com", "Luis", role, privilege))
        course = courseRepository.saveAndFlush(Course("EIF411", "Móviles"))
        assessment = assessments.create(course.id!!, createRequest())
    }

    private fun createRequest(title: String = "Quiz Backend", createdBy: Long? = user.id) =
        CreateAssessmentRequest(
            title = title,
            description = "Conceptos base",
            durationMinutes = 10,
            createdByUserId = createdBy,
            questions = listOf(
                CreateQuestionRequest("¿Qué método HTTP crea un recurso?", listOf("GET", "POST", "DELETE"), 1),
                CreateQuestionRequest("¿Qué abstrae el patrón Repositorio?", listOf("El acceso a datos", "La interfaz"), 0),
            ),
        )

    private fun start(userId: Long? = user.id) = attempts.start(assessment.id, StartAttemptRequest(userId))

    @Test
    fun `crear una evaluacion la asocia al curso y conserva sus preguntas`() {
        assertEquals(course.id, assessment.courseId)
        assertEquals("Móviles", assessment.courseName)
        assertEquals(user.id, assessment.createdByUserId)
        assertEquals(2, assessment.totalQuestions)
        assertEquals(listOf(0, 1), assessment.questions.map { it.index })
        assertEquals(listOf("GET", "POST", "DELETE"), assessment.questions[0].options)
        assertEquals(assessment, assessments.findById(assessment.id))
        assertEquals(listOf(assessment.id), assessments.findByCourse(course.id!!).map { it.id })
    }

    @Test
    fun `crear una evaluacion normaliza el titulo y permite no indicar creador`() {
        val created = assessments.create(course.id!!, createRequest(title = " Quiz BD ", createdBy = null))
        assertEquals("Quiz BD", created.title)
        assertNull(created.createdByUserId)
    }

    @Test
    fun `crear una evaluacion rechaza titulo duplicado, curso o creador inexistente`() {
        assertThrows<ConflictException> { assessments.create(course.id!!, createRequest()) }
        assertThrows<NotFoundException> { assessments.create(missingId, createRequest("Otro")) }
        assertThrows<NotFoundException> { assessments.create(course.id!!, createRequest("Otro", createdBy = missingId)) }
    }

    @Test
    fun `crear una evaluacion rechaza preguntas invalidas`() {
        fun withQuestion(question: CreateQuestionRequest) =
            createRequest("Inválida").copy(questions = listOf(question))

        assertThrows<BadRequestException> {
            assessments.create(course.id!!, withQuestion(CreateQuestionRequest("P", listOf("a", "b"), 2)))
        }
        assertThrows<BadRequestException> {
            assessments.create(course.id!!, withQuestion(CreateQuestionRequest("P", listOf("a", " "), 0)))
        }
        assertThrows<BadRequestException> {
            assessments.create(course.id!!, withQuestion(CreateQuestionRequest("P", listOf("a", "b"), null)))
        }
        assertThrows<BadRequestException> {
            assessments.create(course.id!!, createRequest("Vacía").copy(questions = emptyList()))
        }
    }

    @Test
    fun `consultar evaluaciones inexistentes devuelve not found`() {
        assertThrows<NotFoundException> { assessments.findById(missingId) }
        assertThrows<NotFoundException> { assessments.findByCourse(missingId) }
    }

    @Test
    fun `iniciar un intento lo deja en progreso y sin puntaje`() {
        val started = start()
        assertEquals(AttemptStatus.IN_PROGRESS, started.status)
        assertEquals(assessment.id, started.assessmentId)
        assertEquals(user.id, started.userId)
        assertNull(started.score)
        assertNull(started.completedAt)
        assertTrue(started.answers.isEmpty())
        assertEquals(started, attempts.findById(started.id))
    }

    @Test
    fun `iniciar un intento valida usuario, evaluacion e intento en progreso`() {
        assertThrows<BadRequestException> { start(userId = null) }
        assertThrows<BadRequestException> { start(userId = 0) }
        assertThrows<NotFoundException> { start(userId = missingId) }
        assertThrows<NotFoundException> { attempts.start(missingId, StartAttemptRequest(user.id)) }
        start()
        assertThrows<ConflictException> { start() }
        assertNotNull(start(otherUser.id))
    }

    @Test
    fun `enviar un intento calcula puntaje, respuestas y lo completa`() {
        val started = start()
        val result = attempts.submit(started.id, SubmitAttemptRequest(listOf(1, 1)))

        assertEquals(AttemptStatus.COMPLETED, result.status)
        assertEquals(BigDecimal("50.00"), result.score)
        assertNotNull(result.completedAt)
        assertTrue(result.durationSeconds >= 0)
        assertEquals(listOf(true, false), result.answers.map { it.correct })
        assertEquals(listOf(1, 1), result.answers.map { it.selectedOptionIndex })
    }

    @Test
    fun `enviar un intento con todas correctas da 100 y sin responder cuenta como incorrecta`() {
        val perfect = attempts.submit(start().id, SubmitAttemptRequest(listOf(1, 0)))
        assertEquals(BigDecimal("100.00"), perfect.score)

        val partial = attempts.submit(start().id, SubmitAttemptRequest(listOf(1, null)))
        assertEquals(BigDecimal("50.00"), partial.score)
        assertFalse(partial.answers[1].correct)
        assertNull(partial.answers[1].selectedOptionIndex)
    }

    @Test
    fun `enviar un intento ya completado devuelve conflict y no cambia el resultado`() {
        val started = start()
        val first = attempts.submit(started.id, SubmitAttemptRequest(listOf(1, 0)))

        assertThrows<ConflictException> { attempts.submit(started.id, SubmitAttemptRequest(listOf(0, 1))) }
        assertEquals(first, attempts.findById(started.id))
    }

    @Test
    fun `enviar un intento valida respuestas e intento existente`() {
        val started = start()
        assertThrows<BadRequestException> { attempts.submit(started.id, SubmitAttemptRequest(null)) }
        assertThrows<BadRequestException> { attempts.submit(started.id, SubmitAttemptRequest(listOf(1))) }
        assertThrows<BadRequestException> { attempts.submit(started.id, SubmitAttemptRequest(listOf(1, 5))) }
        assertThrows<BadRequestException> { attempts.submit(started.id, SubmitAttemptRequest(listOf(-1, 0))) }
        assertThrows<NotFoundException> { attempts.submit(missingId, SubmitAttemptRequest(listOf(1, 0))) }
        assertEquals(AttemptStatus.IN_PROGRESS, attempts.findById(started.id).status)
    }

    @Test
    fun `se puede iniciar un nuevo intento despues de completar el anterior`() {
        val first = start()
        attempts.submit(first.id, SubmitAttemptRequest(listOf(1, 0)))
        val second = start()
        assertEquals(AttemptStatus.IN_PROGRESS, second.status)
        assertFalse(first.id == second.id)
    }

    @Test
    fun `listar intentos de un usuario los devuelve del mas reciente al mas antiguo`() {
        val stored = assessmentRepository.findById(assessment.id).orElseThrow()
        val old = attemptRepository.save(
            AssessmentAttempt(assessment = stored, user = user, startedAt = Instant.parse("2026-09-20T10:00:00Z"))
        )
        val recent = attemptRepository.save(
            AssessmentAttempt(assessment = stored, user = user, startedAt = Instant.parse("2026-09-25T10:00:00Z"))
        )
        attemptRepository.save(
            AssessmentAttempt(assessment = stored, user = otherUser, startedAt = Instant.parse("2026-09-26T10:00:00Z"))
        )
        entityManager.flush()
        entityManager.clear()

        assertEquals(listOf(recent.id, old.id), attempts.findByUser(user.id!!).map { it.id })
        assertTrue(attempts.findByUser(otherUser.id!!).size == 1)
    }

    @Test
    fun `consultar intentos inexistentes o de un usuario inexistente devuelve not found`() {
        assertThrows<NotFoundException> { attempts.findById(missingId) }
        assertThrows<NotFoundException> { attempts.findByUser(missingId) }
    }
}