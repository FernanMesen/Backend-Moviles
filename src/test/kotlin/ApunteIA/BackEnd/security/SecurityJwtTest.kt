package ApunteIA.BackEnd.security

import ApunteIA.BackEnd.entity.UserStatus
import ApunteIA.BackEnd.repository.UserRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals

/**
 * Pruebas de "Security with JWT" con tokens reales (sin @WithMockUser):
 * login → JWT, rutas públicas, 401 sin token / token alterado / vencido / cuenta bloqueada, 403 por rol.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = ["spring.datasource.url=jdbc:h2:mem:apunteia-security;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE"])
class SecurityJwtTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val objectMapper: ObjectMapper,
    private val jwtService: JwtService,
    private val userRepository: UserRepository,
    private val transactionTemplate: TransactionTemplate,
) {
    private fun login(email: String, password: String): String {
        val body = mockMvc.post("/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"$email","password":"$password"}"""
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString
        return objectMapper.readTree(body)["token"].asText()
    }

    private fun studentToken() = login("demo@apunteia.com", "123456")
    private fun adminToken() = login("admin@apunteia.com", "Admin123!")
    private fun demoId() = requireNotNull(userRepository.findByEmail("demo@apunteia.com")?.id)

    // ---------- Servicio de JWT ----------

    @Test
    fun `el login devuelve un JWT con id, email y rol del usuario`() {
        val token = studentToken()
        assertEquals(3, token.split(".").size, "Un JWT tiene header.payload.firma")

        val user = jwtService.parse(token)
        assertEquals(demoId(), user.id)
        assertEquals("demo@apunteia.com", user.email)
        assertEquals("STUDENT", user.role)
    }

    // ---------- Rutas públicas ----------

    @Test
    fun `login y registro no piden token`() {
        mockMvc.post("/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"fullName":"Sin Token","email":"jwt-${UUID.randomUUID()}@apunteia.com","password":"123456"}"""
        }.andExpect { status { isCreated() } }
    }

    // ---------- 401 ----------

    @Test
    fun `ruta protegida sin token devuelve 401 en formato ErrorResponse`() {
        mockMvc.get("/courses").andExpect {
            status { isUnauthorized() }
            header { string(HttpHeaders.WWW_AUTHENTICATE, "Bearer") }
            jsonPath("$.status") { value(401) }
            jsonPath("$.message") { value(containsString("Falta el token")) }
            jsonPath("$.path") { value("/courses") }
        }
    }

    @Test
    fun `token con la firma alterada devuelve 401`() {
        val token = studentToken()
        val tampered = token.dropLast(4) + if (token.endsWith("AAAA")) "BBBB" else "AAAA"
        mockMvc.get("/courses") { header(HttpHeaders.AUTHORIZATION, "Bearer $tampered") }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.message") { value("El token no es válido") }
        }
    }

    @Test
    fun `token mal formado o sin prefijo Bearer devuelve 401`() {
        mockMvc.get("/courses") { header(HttpHeaders.AUTHORIZATION, "Bearer abc.def") }
            .andExpect { status { isUnauthorized() } }
        mockMvc.get("/courses") { header(HttpHeaders.AUTHORIZATION, studentToken()) }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.message") { value(containsString("Bearer <token>")) }
        }
    }

    @Test
    fun `token vencido devuelve 401 con mensaje de expiración`() {
        val user = transactionTemplate.execute { userRepository.findByEmail("demo@apunteia.com")!!.also { it.role.name } }!!
        val expired = jwtService.generate(user, issuedAt = Instant.now().minusSeconds(jwtService.expirationSeconds + 60))
        mockMvc.get("/courses") { header(HttpHeaders.AUTHORIZATION, "Bearer $expired") }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.message") { value(containsString("expiró")) }
        }
    }

    @Test
    fun `token de un usuario que luego fue bloqueado deja de servir`() {
        val email = "jwt-${UUID.randomUUID()}@apunteia.com"
        mockMvc.post("/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"fullName":"Se Bloquea","email":"$email","password":"123456"}"""
        }.andExpect { status { isCreated() } }
        val token = login(email, "123456")
        mockMvc.get("/courses") { header(HttpHeaders.AUTHORIZATION, "Bearer $token") }.andExpect { status { isOk() } }

        val id = userRepository.findByEmail(email)!!.id
        mockMvc.patch("/users/$id/status") {
            header(HttpHeaders.AUTHORIZATION, "Bearer ${adminToken()}")
            contentType = MediaType.APPLICATION_JSON
            content = """{"status":"BLOCKED"}"""
        }.andExpect { status { isOk() } }
        assertEquals(UserStatus.BLOCKED, userRepository.findByEmail(email)!!.status)

        mockMvc.get("/courses") { header(HttpHeaders.AUTHORIZATION, "Bearer $token") }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.message") { value("La cuenta del token ya no está activa") }
        }
    }

    // ---------- 200 y 403 según el rol ----------

    @Test
    fun `estudiante con token válido accede a rutas protegidas`() {
        mockMvc.get("/courses") { header(HttpHeaders.AUTHORIZATION, "Bearer ${studentToken()}") }
            .andExpect { status { isOk() } }
    }

    @Test
    fun `estudiante no puede usar rutas de ADMIN y recibe 403`() {
        val token = studentToken()
        mockMvc.get("/security-logs") { header(HttpHeaders.AUTHORIZATION, "Bearer $token") }.andExpect {
            status { isForbidden() }
            jsonPath("$.status") { value(403) }
            jsonPath("$.message") { value("No tiene permisos para realizar esta acción") }
        }
        mockMvc.patch("/users/${demoId()}/status") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = """{"status":"BLOCKED"}"""
        }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `admin sí puede consultar la auditoría`() {
        mockMvc.get("/security-logs") { header(HttpHeaders.AUTHORIZATION, "Bearer ${adminToken()}") }
            .andExpect { status { isOk() } }
    }

    @Test
    fun `STUDENT crea cursos y ADMIN administra cursos con syllabus e inscripciones`() {
        val student = studentToken()
        val code = "STU-${UUID.randomUUID()}"

        mockMvc.post("/courses") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $student")
            contentType = MediaType.APPLICATION_JSON
            content = """{"code":"$code","name":"Curso admin"}"""
        }.andExpect { status { isCreated() } }

        val loginBody = mockMvc.post("/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"admin@apunteia.com","password":"Admin123!"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.role") { value("ADMIN") }
        }.andReturn().response.contentAsString
        val admin = objectMapper.readTree(loginBody)["token"].asText()
        val adminCode = "ADM-${UUID.randomUUID()}"

        val courseBody = mockMvc.post("/courses") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $admin")
            contentType = MediaType.APPLICATION_JSON
            content = """{"code":"$adminCode","name":"Curso admin","syllabus":"Tema 1\nTema 2"}"""
        }.andExpect {
            status { isCreated() }
            jsonPath("$.syllabus") { value("Tema 1\nTema 2") }
        }.andReturn().response.contentAsString
        val courseId = objectMapper.readTree(courseBody)["id"].asLong()

        mockMvc.put("/courses/$courseId") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $admin")
            contentType = MediaType.APPLICATION_JSON
            content = """{"code":"$adminCode","name":"Curso actualizado","syllabus":"Tema actualizado"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.syllabus") { value("Tema actualizado") }
        }
        mockMvc.put("/courses/$courseId") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $student")
            contentType = MediaType.APPLICATION_JSON
            content = """{"code":"$adminCode","name":"Curso no autorizado"}"""
        }.andExpect { status { isForbidden() } }

        val enrollmentBody = mockMvc.post("/courses/$courseId/enrollments") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $admin")
            contentType = MediaType.APPLICATION_JSON
            content = """{"userId":${demoId()}}"""
        }.andExpect { status { isCreated() } }.andReturn().response.contentAsString
        val enrollmentId = objectMapper.readTree(enrollmentBody)["id"].asLong()
        mockMvc.get("/enrollments?courseId=$courseId") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $admin")
        }.andExpect {
            status { isOk() }
            jsonPath("$[0].userFullName") { exists() }
            jsonPath("$[0].userEmail") { exists() }
        }
        mockMvc.delete("/enrollments/$enrollmentId") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $student")
        }.andExpect { status { isNoContent() } }
        mockMvc.delete("/courses/$courseId") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $admin")
        }.andExpect { status { isNoContent() } }
    }
}
