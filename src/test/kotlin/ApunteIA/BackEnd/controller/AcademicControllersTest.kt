package ApunteIA.BackEnd.controller

import ApunteIA.BackEnd.entity.Course
import ApunteIA.BackEnd.entity.SecurityLog
import ApunteIA.BackEnd.repository.CourseRepository
import ApunteIA.BackEnd.repository.SecurityLogRepository
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
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** API real sobre H2, sin transacción de prueba que oculte problemas de acceso LAZY. */
@SpringBootTest
@AutoConfigureMockMvc
// Estas pruebas validan la lógica de cada endpoint; la seguridad JWT se prueba en SecurityJwtTest
@WithMockUser(roles = ["ADMIN"])
@TestPropertySource(properties = ["spring.datasource.url=jdbc:h2:mem:apunteia-academic-web;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE"])
class AcademicControllersTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val objectMapper: ObjectMapper,
    private val courseRepository: CourseRepository,
    private val userRepository: UserRepository,
    private val securityLogRepository: SecurityLogRepository,
) {
    private lateinit var course: Course
    private var userId: Long = 0
    private val missingId = Long.MAX_VALUE

    @BeforeEach
    fun setUp() {
        course = courseRepository.saveAndFlush(Course("T-${UUID.randomUUID()}", "Curso de prueba"))
        userId = userRepository.findByEmail("demo@apunteia.com")!!.id!!
    }

    private fun createEnrollment() = mockMvc.post("/enrollments") {
        contentType = MediaType.APPLICATION_JSON
        content = """{"userId":$userId,"courseId":${course.id}}"""
    }

    private fun enrollViaCourse(courseId: Long = course.id!!, targetUserId: Long = userId) =
        mockMvc.post("/courses/$courseId/enrollments") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"userId":$targetUserId}"""
        }

    @Test
    fun `POST course enrollment alias returns 201 and canonical Location`() {
        val result = enrollViaCourse().andExpect {
            status { isCreated() }
            jsonPath("$.userId") { value(userId) }
            jsonPath("$.courseId") { value(course.id) }
        }.andReturn()
        val id = objectMapper.readTree(result.response.contentAsString)["id"].asLong()
        assertEquals("http://localhost/enrollments/$id", result.response.getHeader("Location"))
        mockMvc.get("/enrollments/$id").andExpect {
            status { isOk() }
            jsonPath("$.userId") { value(userId) }
            jsonPath("$.courseId") { value(course.id) }
        }
    }

    @Test
    fun `POST course enrollment alias rejects duplicate created by original endpoint`() {
        createEnrollment().andExpect { status { isCreated() } }
        enrollViaCourse().andExpect { status { isConflict() } }
    }

    @Test
    fun `POST course enrollment alias rejects missing user`() {
        enrollViaCourse(targetUserId = missingId).andExpect {
            status { isNotFound() }
            jsonPath("$.message") { value("No existe un usuario con id $missingId") }
        }
    }

    @Test
    fun `POST course enrollment alias rejects missing course`() {
        enrollViaCourse(courseId = missingId).andExpect {
            status { isNotFound() }
            jsonPath("$.message") { value("No existe un curso con id $missingId") }
        }
    }

    @Test
    fun `POST course enrollment alias validates required positive user id`() {
        for (body in listOf("{}", """{"userId":null}""", """{"userId":0}""", """{"userId":-1}""")) {
            mockMvc.post("/courses/${course.id}/enrollments") {
                contentType = MediaType.APPLICATION_JSON
                content = body
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.fieldErrors.userId") { exists() }
            }
        }
    }

    @Test
    fun `GET user enrollments alias returns 200 with empty list when none exist`() {
        mockMvc.get("/users/$missingId/enrollments").andExpect {
            status { isOk() }
            jsonPath("$") { isArray() }
            jsonPath("$.length()") { value(0) }
        }
    }

    @Test
    fun `GET user enrollments alias includes only target user and matches original endpoint`() {
        val created = createEnrollment().andExpect { status { isCreated() } }.andReturn()
        val createdId = objectMapper.readTree(created.response.contentAsString)["id"].asLong()
        val otherUserId = userRepository.findAll().first { it.id != userId }.id!!
        enrollViaCourse(targetUserId = otherUserId).andExpect { status { isCreated() } }
        val alias = mockMvc.get("/users/$userId/enrollments").andExpect {
            status { isOk() }
            jsonPath("$[*].userId") { value(everyItem(`is`(userId.toInt()))) }
        }.andReturn()
        val original = mockMvc.get("/enrollments?userId=$userId").andExpect {
            status { isOk() }
        }.andReturn()
        val result = objectMapper.readTree(alias.response.contentAsString)
        assertTrue(result.any { it["id"].asLong() == createdId })
        assertEquals(objectMapper.readTree(original.response.contentAsString), result)
    }

    @Test
    fun `GET courses devuelve 200 y lista de DTOs`() {
        mockMvc.get("/courses").andExpect {
            status { isOk() }
            jsonPath("$") { isArray() }
            jsonPath("$[0].code") { exists() }
        }
    }

    @Test
    fun `GET course existente devuelve 200`() {
        mockMvc.get("/courses/${course.id}").andExpect {
            status { isOk() }
            jsonPath("$.id") { value(course.id) }
            jsonPath("$.name") { value("Curso de prueba") }
        }
    }

    @Test
    fun `GET course inexistente devuelve 404 y formato invalido 400`() {
        mockMvc.get("/courses/$missingId").andExpect {
            status { isNotFound() }
            jsonPath("$.message") { value("No existe un curso con id $missingId") }
        }
        mockMvc.get("/courses/abc").andExpect { status { isBadRequest() } }
    }

    @Test
    fun `POST course valido devuelve 201 DTO y Location`() {
        val code = "N-${UUID.randomUUID()}"
        val result = mockMvc.post("/courses") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"code":" $code ","name":" Nuevo ","description":" Descripción "}"""
        }.andExpect {
            status { isCreated() }
            jsonPath("$.code") { value(code) }
            jsonPath("$.name") { value("Nuevo") }
            jsonPath("$.description") { value("Descripción") }
        }.andReturn()
        val id = objectMapper.readTree(result.response.contentAsString)["id"].asLong()
        mockMvc.get(result.response.getHeader("Location")!!).andExpect {
            status { isOk() }
            jsonPath("$.id") { value(id) }
        }
    }

    @Test
    fun `POST course invalido devuelve 400 con errores de campos`() {
        mockMvc.post("/courses") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"code":" ","name":""}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.fieldErrors.code") { exists() }
            jsonPath("$.fieldErrors.name") { exists() }
        }
    }

    @Test
    fun `POST course duplicado devuelve 409`() {
        mockMvc.post("/courses") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"code":"${course.code}","name":"Duplicado"}"""
        }.andExpect { status { isConflict() } }
    }

    @Test
    fun `PUT course valido devuelve 200`() {
        mockMvc.put("/courses/${course.id}") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"code":"${course.code}","name":"Actualizado"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.name") { value("Actualizado") }
        }
    }

    @Test
    fun `PUT course inexistente duplicado o invalido devuelve errores correspondientes`() {
        val other = courseRepository.saveAndFlush(Course("O-${UUID.randomUUID()}", "Otro"))
        mockMvc.put("/courses/$missingId") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"code":"${course.code}","name":"Curso"}"""
        }.andExpect { status { isNotFound() } }
        mockMvc.put("/courses/${course.id}") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"code":"${other.code}","name":"Curso"}"""
        }.andExpect { status { isConflict() } }
        mockMvc.put("/courses/${course.id}") {
            contentType = MediaType.APPLICATION_JSON
            content = "{}"
        }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `DELETE course devuelve 204 sin cuerpo y luego 404`() {
        mockMvc.delete("/courses/${course.id}").andExpect {
            status { isNoContent() }
            content { string("") }
        }
        mockMvc.get("/courses/${course.id}").andExpect { status { isNotFound() } }
        mockMvc.delete("/courses/${course.id}").andExpect { status { isNotFound() } }
    }

    @Test
    fun `DELETE course con inscripcion devuelve 409 y conserva registros`() {
        createEnrollment().andExpect { status { isCreated() } }
        mockMvc.delete("/courses/${course.id}").andExpect { status { isConflict() } }
        mockMvc.get("/courses/${course.id}").andExpect { status { isOk() } }
        mockMvc.get("/enrollments?courseId=${course.id}").andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(1) }
        }
    }

    @Test
    fun `POST course con content type incorrecto devuelve 415 y JSON roto 400`() {
        mockMvc.post("/courses") {
            contentType = MediaType.TEXT_PLAIN
            content = "curso"
        }.andExpect { status { isUnsupportedMediaType() } }
        mockMvc.post("/courses") {
            contentType = MediaType.APPLICATION_JSON
            content = "{"
        }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `GET enrollments devuelve 200 y combina filtros`() {
        createEnrollment().andExpect { status { isCreated() } }
        mockMvc.get("/enrollments").andExpect { status { isOk() }; jsonPath("$") { isArray() } }
        mockMvc.get("/enrollments?userId=$userId").andExpect {
            status { isOk() }
            jsonPath("$[*].userId") { value(everyItem(`is`(userId.toInt()))) }
        }
        mockMvc.get("/enrollments?courseId=${course.id}").andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(1) }
        }
        mockMvc.get("/enrollments?userId=$userId&courseId=${course.id}").andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(1) }
        }
        mockMvc.get("/enrollments?userId=$missingId&courseId=${course.id}").andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(0) }
        }
    }

    @Test
    fun `POST enrollment devuelve 201 Location y DTO sin entidades ni password`() {
        val result = createEnrollment().andExpect {
            status { isCreated() }
            header { string("Location", containsString("/enrollments/")) }
            jsonPath("$.userId") { value(userId) }
            jsonPath("$.courseCode") { value(course.code) }
            jsonPath("$.enrolledAt") { exists() }
            jsonPath("$.passwordHash") { doesNotExist() }
            jsonPath("$.user") { doesNotExist() }
            jsonPath("$.course") { doesNotExist() }
        }.andReturn()
        mockMvc.get(result.response.getHeader("Location")!!).andExpect { status { isOk() } }
    }

    @Test
    fun `POST enrollment duplicado devuelve 409`() {
        createEnrollment().andExpect { status { isCreated() } }
        createEnrollment().andExpect { status { isConflict() } }
    }

    @Test
    fun `POST enrollment con usuario o curso inexistente devuelve 404`() {
        mockMvc.post("/enrollments") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"userId":$missingId,"courseId":${course.id}}"""
        }.andExpect { status { isNotFound() } }
        mockMvc.post("/enrollments") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"userId":$userId,"courseId":$missingId}"""
        }.andExpect { status { isNotFound() } }
    }

    @Test
    fun `POST enrollment con ids ausentes o no positivos devuelve 400`() {
        for (body in listOf("{}", """{"userId":0,"courseId":-1}""")) {
            mockMvc.post("/enrollments") {
                contentType = MediaType.APPLICATION_JSON
                content = body
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.fieldErrors.userId") { exists() }
                jsonPath("$.fieldErrors.courseId") { exists() }
            }
        }
    }

    @Test
    fun `DELETE enrollment devuelve 204 y GET inexistente 404`() {
        val result = createEnrollment().andExpect { status { isCreated() } }.andReturn()
        val id = objectMapper.readTree(result.response.contentAsString)["id"].asLong()
        mockMvc.delete("/enrollments/$id").andExpect {
            status { isNoContent() }
            content { string("") }
        }
        mockMvc.get("/enrollments/$id").andExpect { status { isNotFound() } }
        mockMvc.delete("/enrollments/$id").andExpect { status { isNotFound() } }
    }

    @Test
    fun `PUT y PATCH enrollment no existen`() {
        mockMvc.put("/enrollments/1").andExpect { status { isMethodNotAllowed() } }
        mockMvc.patch("/enrollments/1").andExpect { status { isMethodNotAllowed() } }
    }

    @Test
    fun `GET security logs devuelve 200 y filtra usuario evento y anonimos`() {
        val event = "T-${UUID.randomUUID()}"
        val user = userRepository.findById(userId).orElseThrow()
        securityLogRepository.saveAndFlush(SecurityLog(event, user))
        securityLogRepository.saveAndFlush(SecurityLog(event))
        mockMvc.get("/security-logs").andExpect { status { isOk() }; jsonPath("$") { isArray() } }
        mockMvc.get("/security-logs?userId=$userId").andExpect {
            status { isOk() }
            jsonPath("$[*].userId") { value(everyItem(`is`(userId.toInt()))) }
        }
        mockMvc.get("/security-logs?eventType=$event").andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(2) }
        }
        mockMvc.get("/security-logs?anonymousOnly=true&eventType=$event").andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(1) }
            jsonPath("$[0].userId") { doesNotExist() }
            jsonPath("$[0].userFullName") { doesNotExist() }
        }
        mockMvc.get("/security-logs") { param("userId", "$userId"); param("eventType", " $event ") }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(1) }
        }
        mockMvc.get("/security-logs?userId=$userId&anonymousOnly=true").andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(0) }
        }
    }

    @Test
    fun `GET security log existente y anonimo devuelve DTO y ausente 404`() {
        val user = userRepository.findById(userId).orElseThrow()
        val identified = securityLogRepository.saveAndFlush(SecurityLog("LOGIN", user))
        val anonymous = securityLogRepository.saveAndFlush(SecurityLog("LOGIN_FAILED"))
        mockMvc.get("/security-logs/${identified.id}").andExpect {
            status { isOk() }
            jsonPath("$.userId") { value(userId) }
            jsonPath("$.userFullName") { exists() }
            jsonPath("$.user") { doesNotExist() }
            jsonPath("$.passwordHash") { doesNotExist() }
        }
        mockMvc.get("/security-logs/${anonymous.id}").andExpect {
            status { isOk() }
            jsonPath("$.userId") { doesNotExist() }
        }
        mockMvc.get("/security-logs/$missingId").andExpect { status { isNotFound() } }
    }

    @Test
    fun `security logs no admite POST PUT PATCH ni DELETE`() {
        for (path in listOf("/security-logs", "/security-logs/1")) {
            mockMvc.post(path).andExpect { status { isMethodNotAllowed() } }
            mockMvc.put(path).andExpect { status { isMethodNotAllowed() } }
            mockMvc.patch(path).andExpect { status { isMethodNotAllowed() } }
            mockMvc.delete(path).andExpect { status { isMethodNotAllowed() } }
        }
    }

    @Test
    fun `query params con formato invalido devuelven 400`() {
        mockMvc.get("/enrollments?userId=abc").andExpect { status { isBadRequest() } }
        mockMvc.get("/enrollments?courseId=abc").andExpect { status { isBadRequest() } }
        mockMvc.get("/security-logs?userId=abc").andExpect { status { isBadRequest() } }
        mockMvc.get("/security-logs?anonymousOnly=abc").andExpect { status { isBadRequest() } }
    }
}