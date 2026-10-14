package ApunteIA.BackEnd.controller

import ApunteIA.BackEnd.entity.Course
import ApunteIA.BackEnd.repository.CourseRepository
import ApunteIA.BackEnd.repository.UserRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.everyItem
import org.hamcrest.Matchers.`is`
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = ["spring.datasource.url=jdbc:h2:mem:apunteia-assessment-web;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE"])
class AssessmentControllersTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val objectMapper: ObjectMapper,
    private val courseRepository: CourseRepository,
    private val userRepository: UserRepository,
) {
    private lateinit var course: Course
    private var userId: Long = 0
    private var creatorId: Long = 0
    private val missingId = Long.MAX_VALUE

    @BeforeEach
    fun setUp() {
        course = courseRepository.saveAndFlush(Course("A-${UUID.randomUUID()}", "Curso de evaluaciones"))
        userId = userRepository.findByEmail("demo@apunteia.com")!!.id!!
        creatorId = userRepository.findByEmail("profe@apunteia.com")!!.id!!
    }

    private fun assessmentBody(title: String, correctOptionIndex: Int = 1, createdBy: Long? = creatorId) = """
        {
          "title": "$title",
          "description": "Evaluación de prueba",
          "difficulty": "EASY",
          "durationMinutes": 10,
          "createdByUserId": ${createdBy ?: "null"},
          "questions": [
            {"text": "¿Qué método HTTP crea un recurso?", "options": ["GET", "POST", "DELETE"], "correctOptionIndex": $correctOptionIndex},
            {"text": "¿Qué abstrae el patrón Repositorio?", "options": ["El acceso a datos", "La interfaz"], "correctOptionIndex": 0}
          ]
        }
    """.trimIndent()

    private fun createAssessment(
        courseId: Long = course.id!!,
        title: String = "Quiz ${UUID.randomUUID()}",
        body: String = assessmentBody(title),
    ) = mockMvc.post("/courses/$courseId/assessments") {
        contentType = MediaType.APPLICATION_JSON
        content = body
    }

    private fun createAssessmentId(): Long {
        val result = createAssessment().andExpect { status { isCreated() } }.andReturn()
        return objectMapper.readTree(result.response.contentAsString)["id"].asLong()
    }

    private fun startAttempt(assessmentId: Long, targetUserId: Long = userId) =
        mockMvc.post("/assessments/$assessmentId/attempts") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"userId":$targetUserId}"""
        }

    private fun startAttemptId(assessmentId: Long, targetUserId: Long = userId): Long {
        val result = startAttempt(assessmentId, targetUserId).andExpect { status { isCreated() } }.andReturn()
        return objectMapper.readTree(result.response.contentAsString)["id"].asLong()
    }

    private fun submit(attemptId: Long, body: String) = mockMvc.patch("/attempts/$attemptId/submit") {
        contentType = MediaType.APPLICATION_JSON
        content = body
    }

    @Test
    fun `POST course assessment returns 201, Location and hides correct answers`() {
        val title = "Quiz ${UUID.randomUUID()}"
        val result = createAssessment(title = title).andExpect {
            status { isCreated() }
            jsonPath("$.title") { value(title) }
            jsonPath("$.courseId") { value(course.id) }
            jsonPath("$.createdByUserId") { value(creatorId) }
            jsonPath("$.type") { value("QUIZ") }
            jsonPath("$.totalQuestions") { value(2) }
            jsonPath("$.questions[0].correctOptionIndex") { doesNotExist() }
        }.andReturn()
        val id = objectMapper.readTree(result.response.contentAsString)["id"].asLong()
        assertEquals("http://localhost/assessments/$id", result.response.getHeader("Location"))
        mockMvc.get("/assessments/$id").andExpect {
            status { isOk() }
            jsonPath("$.title") { value(title) }
            jsonPath("$.questions.length()") { value(2) }
        }
    }

    @Test
    fun `POST course assessment rejects invalid requests with 400`() {
        for (body in listOf("{}", """{"title":"","questions":[]}""")) {
            createAssessment(body = body).andExpect {
                status { isBadRequest() }
                jsonPath("$.fieldErrors.title") { exists() }
                jsonPath("$.fieldErrors.questions") { exists() }
            }
        }
        createAssessment(body = assessmentBody("Quiz ${UUID.randomUUID()}", correctOptionIndex = 7)).andExpect {
            status { isBadRequest() }
            jsonPath("$.message") { value(containsString("fuera de rango")) }
        }
        createAssessment(body = assessmentBody("Quiz ${UUID.randomUUID()}").replace("\"EASY\"", "\"FOO\"")).andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `POST course assessment returns 404 for missing course or creator`() {
        createAssessment(courseId = missingId).andExpect {
            status { isNotFound() }
            jsonPath("$.message") { value("No existe un curso con id $missingId") }
        }
        val title = "Quiz ${UUID.randomUUID()}"
        createAssessment(body = assessmentBody(title, createdBy = missingId)).andExpect {
            status { isNotFound() }
            jsonPath("$.message") { value("No existe un usuario con id $missingId") }
        }
    }

    @Test
    fun `POST course assessment returns 409 for duplicate title and 415 for wrong content type`() {
        val title = "Quiz ${UUID.randomUUID()}"
        createAssessment(title = title).andExpect { status { isCreated() } }
        createAssessment(title = title).andExpect { status { isConflict() } }
        mockMvc.post("/courses/${course.id}/assessments") {
            contentType = MediaType.TEXT_PLAIN
            content = "title=x"
        }.andExpect { status { isUnsupportedMediaType() } }
    }

    @Test
    fun `GET course assessments returns only the assessments of that course`() {
        val id = createAssessmentId()
        val otherCourse = courseRepository.saveAndFlush(Course("A-${UUID.randomUUID()}", "Otro curso"))
        createAssessment(courseId = otherCourse.id!!).andExpect { status { isCreated() } }
        mockMvc.get("/courses/${course.id}/assessments").andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(1) }
            jsonPath("$[0].id") { value(id) }
        }
    }

    @Test
    fun `GET course assessments returns empty list or 404`() {
        mockMvc.get("/courses/${course.id}/assessments").andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(0) }
        }
        mockMvc.get("/courses/$missingId/assessments").andExpect {
            status { isNotFound() }
            jsonPath("$.message") { value("No existe un curso con id $missingId") }
        }
    }

    @Test
    fun `GET assessment by id returns 404 for missing and 400 for non numeric id`() {
        mockMvc.get("/assessments/$missingId").andExpect {
            status { isNotFound() }
            jsonPath("$.message") { value("No existe una evaluación con id $missingId") }
        }
        mockMvc.get("/assessments/abc").andExpect { status { isBadRequest() } }
    }

    @Test
    fun `GET seeded assessments of EIF411 are listed`() {
        val seedCourseId = courseRepository.findByCode("EIF411")!!.id!!
        mockMvc.get("/courses/$seedCourseId/assessments").andExpect {
            status { isOk() }
            jsonPath("$[*].title") { value(org.hamcrest.Matchers.hasItem("Introducción al Backend")) }
        }
    }

    @Test
    fun `POST assessment attempt returns 201 with Location and an in progress attempt`() {
        val assessmentId = createAssessmentId()
        val result = startAttempt(assessmentId).andExpect {
            status { isCreated() }
            jsonPath("$.status") { value("IN_PROGRESS") }
            jsonPath("$.userId") { value(userId) }
            jsonPath("$.assessmentId") { value(assessmentId) }
            jsonPath("$.answers.length()") { value(0) }
        }.andReturn()
        val id = objectMapper.readTree(result.response.contentAsString)["id"].asLong()
        assertEquals("http://localhost/attempts/$id", result.response.getHeader("Location"))
        mockMvc.get("/attempts/$id").andExpect {
            status { isOk() }
            jsonPath("$.status") { value("IN_PROGRESS") }
        }
    }

    @Test
    fun `POST assessment attempt rejects duplicates, invalid body and missing resources`() {
        val assessmentId = createAssessmentId()
        startAttemptId(assessmentId)
        startAttempt(assessmentId).andExpect { status { isConflict() } }
        mockMvc.post("/assessments/$assessmentId/attempts") {
            contentType = MediaType.APPLICATION_JSON
            content = "{}"
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.fieldErrors.userId") { exists() }
        }
        startAttempt(assessmentId, targetUserId = missingId).andExpect {
            status { isNotFound() }
            jsonPath("$.message") { value("No existe un usuario con id $missingId") }
        }
        startAttempt(missingId).andExpect {
            status { isNotFound() }
            jsonPath("$.message") { value("No existe una evaluación con id $missingId") }
        }
    }

    @Test
    fun `PATCH attempt submit returns 200 with score and completes the attempt`() {
        val attemptId = startAttemptId(createAssessmentId())
        submit(attemptId, """{"answers":[1,1]}""").andExpect {
            status { isOk() }
            jsonPath("$.status") { value("COMPLETED") }
            jsonPath("$.score") { value(50.0) }
            jsonPath("$.answers[0].correct") { value(true) }
            jsonPath("$.answers[1].correct") { value(false) }
        }
        mockMvc.get("/attempts/$attemptId").andExpect {
            status { isOk() }
            jsonPath("$.status") { value("COMPLETED") }
        }
    }

    @Test
    fun `PATCH attempt submit returns 409 for an already completed attempt and keeps the result`() {
        val attemptId = startAttemptId(createAssessmentId())
        submit(attemptId, """{"answers":[1,0]}""").andExpect {
            status { isOk() }
            jsonPath("$.score") { value(100.0) }
        }
        submit(attemptId, """{"answers":[0,1]}""").andExpect {
            status { isConflict() }
            jsonPath("$.message") { value("El intento $attemptId ya fue completado") }
        }
        mockMvc.get("/attempts/$attemptId").andExpect {
            jsonPath("$.score") { value(100.0) }
        }
    }

    @Test
    fun `PATCH attempt submit validates answers and missing attempt`() {
        val attemptId = startAttemptId(createAssessmentId())
        submit(attemptId, """{"answers":[1]}""").andExpect {
            status { isBadRequest() }
            jsonPath("$.message") { value(containsString("Se esperaban 2 respuestas")) }
        }
        submit(attemptId, """{"answers":[1,9]}""").andExpect { status { isBadRequest() } }
        submit(attemptId, "{}").andExpect {
            status { isBadRequest() }
            jsonPath("$.fieldErrors.answers") { exists() }
        }
        submit(attemptId, """{"answers":""").andExpect { status { isBadRequest() } }
        submit(missingId, """{"answers":[1,0]}""").andExpect {
            status { isNotFound() }
            jsonPath("$.message") { value("No existe un intento con id $missingId") }
        }
        mockMvc.get("/attempts/$attemptId").andExpect {
            jsonPath("$.status") { value("IN_PROGRESS") }
        }
    }

    @Test
    fun `GET user attempts returns 200 newest first and 404 for missing user`() {
        val assessmentId = createAssessmentId()
        val first = startAttemptId(assessmentId)
        submit(first, """{"answers":[1,0]}""").andExpect { status { isOk() } }
        val second = startAttemptId(assessmentId)
        val result = mockMvc.get("/users/$userId/attempts").andExpect {
            status { isOk() }
            jsonPath("$[*].userId") { value(everyItem(`is`(userId.toInt()))) }
            jsonPath("$[0].id") { value(second) }
        }.andReturn()
        val ids = objectMapper.readTree(result.response.contentAsString).map { it["id"].asLong() }
        assertTrue(first in ids)
        mockMvc.get("/users/$missingId/attempts").andExpect {
            status { isNotFound() }
            jsonPath("$.message") { value("No existe un usuario con id $missingId") }
        }
    }

    @Test
    fun `DELETE attempt is not allowed`() {
        mockMvc.delete("/attempts/1").andExpect { status { isMethodNotAllowed() } }
    }
}