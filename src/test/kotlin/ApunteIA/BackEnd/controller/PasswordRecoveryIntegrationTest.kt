package ApunteIA.BackEnd.controller

import ApunteIA.BackEnd.exception.EmailDeliveryException
import ApunteIA.BackEnd.repository.PasswordResetTokenRepository
import ApunteIA.BackEnd.service.impl.BrevoEmailService
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.web.util.UriComponentsBuilder
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = [
    "spring.datasource.url=jdbc:h2:mem:apunteia-password-recovery;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
    "app.auth.direct-reset.enabled=true",
    "app.jwt.secret=password-recovery-integration-test-secret-0123456789",
])
class PasswordRecoveryIntegrationTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val objectMapper: ObjectMapper,
    private val resetTokenRepository: PasswordResetTokenRepository,
) {
    @MockBean
    private lateinit var brevoEmailService: BrevoEmailService

    @Test
    fun `solicitud envia link y token de un uso cambia password y deja de ser valido`() {
        val email = "recovery-${UUID.randomUUID()}@apunteia.com"
        var sentEmail: String? = null
        var sentLink: String? = null
        Mockito.doAnswer { invocation ->
            sentEmail = invocation.getArgument(0)
            sentLink = invocation.getArgument(1)
            null
        }.`when`(brevoEmailService).sendPasswordReset(anyString(), anyString())
        mockMvc.post("/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"fullName":"Recovery Test","email":"$email","password":"old-password-123"}"""
        }.andExpect { status { isCreated() } }

        mockMvc.post("/auth/forgot-password") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"$email"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.message") { value("Si la cuenta es elegible, podrás continuar con la recuperación.") }
        }

        Mockito.verify(brevoEmailService).sendPasswordReset(anyString(), anyString())
        assertEquals(email, sentEmail)
        val link = requireNotNull(sentLink)
        assertTrue(link.startsWith("apunteia://reset-password?token="))
        val token = UriComponentsBuilder.fromUriString(link).build().queryParams.getFirst("token")
        assertNotNull(token)
        val storedToken = resetTokenRepository.findAll().single()
        assertFalse(storedToken.tokenHash == token, "La base debe guardar el hash, no el token enviado")

        mockMvc.post("/auth/reset-password") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"token":"$token","newPassword":"new-password-456"}"""
        }.andExpect { status { isNoContent() } }

        mockMvc.post("/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"$email","password":"new-password-456"}"""
        }.andExpect { status { isOk() } }
        mockMvc.post("/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"$email","password":"old-password-123"}"""
        }.andExpect { status { isUnauthorized() } }
        assertTrue(resetTokenRepository.findAll().isEmpty())

        mockMvc.post("/auth/reset-password") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"token":"$token","newPassword":"another-password-789"}"""
        }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `Brevo falla y el usuario desconocido reciben la misma respuesta publica generica`() {
        val email = "recovery-failure-${UUID.randomUUID()}@apunteia.com"
        mockMvc.post("/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"fullName":"Recovery Test","email":"$email","password":"old-password-123"}"""
        }.andExpect { status { isCreated() } }

        Mockito.doAnswer { throw EmailDeliveryException() }
            .`when`(brevoEmailService).sendPasswordReset(anyString(), anyString())

        val eligibleResponse = forgot(email)
        val unknownEmail = "missing-${UUID.randomUUID()}@apunteia.com"
        val unknownResponse = forgot(unknownEmail)

        assertEquals(eligibleResponse, unknownResponse)
        assertEquals("Si la cuenta es elegible, podrás continuar con la recuperación.", objectMapper.readTree(eligibleResponse)["message"].asText())
        assertTrue(resetTokenRepository.findAll().isEmpty(), "No debe persistir token si Brevo no aceptó el mensaje")
    }

    @Test
    fun `recuperacion directa de cuenta ficticia permite nueva clave e invalida la anterior`() {
        mockMvc.post("/auth/direct-reset-password") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"demo@apunteia.com","newPassword":"new-demo-password"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.message") { value("Contraseña actualizada correctamente") }
        }

        mockMvc.post("/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"demo@apunteia.com","password":"new-demo-password"}"""
        }.andExpect { status { isOk() } }

        mockMvc.post("/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"demo@apunteia.com","password":"123456"}"""
        }.andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `recuperacion directa rechaza cuentas que no son ficticias`() {
        mockMvc.post("/auth/direct-reset-password") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"unknown@apunteia.com","newPassword":"new-password"}"""
        }.andExpect { status { isBadRequest() } }
    }

    private fun forgot(email: String): String = mockMvc.post("/auth/forgot-password") {
        contentType = MediaType.APPLICATION_JSON
        content = """{"email":"$email"}"""
    }.andExpect { status { isOk() } }.andReturn().response.contentAsString
}

@SpringBootTest(properties = [
    "spring.datasource.url=jdbc:h2:mem:apunteia-direct-reset-render;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
    "app.auth.direct-reset.enabled=true",
    "app.jwt.secret=direct-reset-deployment-test-secret-0123456789",
])
@AutoConfigureMockMvc
@ActiveProfiles("render")
class DirectResetDeploymentGuardTest @Autowired constructor(
    private val mockMvc: MockMvc,
) {
    @Test
    fun `recuperacion directa se bloquea con perfil render aunque la bandera este habilitada`() {
        mockMvc.post("/auth/direct-reset-password") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"demo@apunteia.com","newPassword":"new-demo-password"}"""
        }.andExpect { status { isForbidden() } }
    }
}
