package ApunteIA.BackEnd.controller

import ApunteIA.BackEnd.repository.AssessmentAttemptRepository
import ApunteIA.BackEnd.repository.DeletedAccountTombstoneRepository
import ApunteIA.BackEnd.repository.EnrollmentRepository
import ApunteIA.BackEnd.repository.NoteRepository
import ApunteIA.BackEnd.repository.PasswordResetTokenRepository
import ApunteIA.BackEnd.repository.SecurityLogRepository
import ApunteIA.BackEnd.repository.UserRepository
import ApunteIA.BackEnd.seed.IdentitySeeder
import com.fasterxml.jackson.databind.ObjectMapper
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
import org.springframework.test.web.servlet.post
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = ["spring.datasource.url=jdbc:h2:mem:apunteia-account-deletion;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE"])
class AccountDeletionIntegrationTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val objectMapper: ObjectMapper,
    private val userRepository: UserRepository,
    private val noteRepository: NoteRepository,
    private val enrollmentRepository: EnrollmentRepository,
    private val attemptRepository: AssessmentAttemptRepository,
    private val securityLogRepository: SecurityLogRepository,
    private val resetTokenRepository: PasswordResetTokenRepository,
    private val tombstoneRepository: DeletedAccountTombstoneRepository,
    private val identitySeeder: IdentitySeeder,
) {
    @Test
    fun `eliminar cuenta borra datos invalida token y bloquea login registro y recuperacion`() {
        val email = "demo@apunteia.com"
        val loginResponse = mockMvc.post("/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"$email","password":"123456"}"""
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString
        val token = objectMapper.readTree(loginResponse)["token"].asText()
        val userId = requireNotNull(userRepository.findByEmail(email)?.id)

        assertTrue(noteRepository.findAllByUser_Id(userId).isNotEmpty())
        assertTrue(enrollmentRepository.findAllByUser_Id(userId).isNotEmpty())
        assertTrue(attemptRepository.findAllByUser_IdOrderByStartedAtDesc(userId).isNotEmpty())
        val userLogIds = securityLogRepository.findAllByUser_IdOrderByOccurredAtDesc(userId).mapNotNull { it.id }
        assertTrue(userLogIds.isNotEmpty())

        mockMvc.delete("/users/me") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $token")
        }.andExpect { status { isNoContent() } }

        assertFalse(userRepository.existsById(userId), "La fila del usuario debe eliminarse físicamente")
        assertTrue(noteRepository.findAllByUser_Id(userId).isEmpty())
        assertTrue(enrollmentRepository.findAllByUser_Id(userId).isEmpty())
        assertTrue(attemptRepository.findAllByUser_IdOrderByStartedAtDesc(userId).isEmpty())
        assertTrue(resetTokenRepository.findAll().none { it.user.id == userId })
        assertTrue(securityLogRepository.findAllById(userLogIds).all { it.user == null && it.details == null && it.ipAddress == null })
        assertEquals(1, tombstoneRepository.count())

        mockMvc.get("/users/me/export") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $token")
        }.andExpect { status { isUnauthorized() } }

        mockMvc.post("/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"$email","password":"123456"}"""
        }.andExpect { status { isUnauthorized() } }

        mockMvc.post("/auth/forgot-password") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"$email"}"""
        }.andExpect { status { isOk() } }
        assertTrue(resetTokenRepository.findAll().isEmpty(), "La recuperación no debe crear tokens para una cuenta eliminada")

        mockMvc.post("/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"fullName":"Demo","email":"$email","password":"123456"}"""
        }.andExpect { status { isConflict() } }

        identitySeeder.seed()
        assertFalse(userRepository.existsById(userId), "El seeder no debe restaurar la cuenta demo eliminada")
    }
}
