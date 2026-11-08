package ApunteIA.BackEnd.service

import ApunteIA.BackEnd.exception.EmailDeliveryException
import ApunteIA.BackEnd.service.impl.BrevoEmailService
import ch.qos.logback.classic.Logger
import ch.qos.logback.core.read.ListAppender
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.web.client.RestClient
import org.springframework.http.HttpStatus
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BrevoEmailServiceTest {
    @Test
    fun `envia con API key de Brevo por HTTPS y usa sender y destinatario configurados`() {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        val sender = BrevoEmailService(builder, "brevo-api-secret", "sender@example.test", "ApunteIA")
        val resetLink = "apunteia://reset-password?token=private-reset-token-123456"

        server.expect(requestTo("https://api.brevo.com/v3/smtp/email"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("api-key", "brevo-api-secret"))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(content().json("""{
                "sender":{"name":"ApunteIA","email":"sender@example.test"},
                "to":[{"email":"person@example.test"}],
                "subject":"Recuperación de contraseña - ApunteIA",
                "textContent":"Usa este enlace para restablecer tu contraseña. Vence en 30 minutos y solo puede usarse una vez:\n\n$resetLink\n\nSi no solicitaste este cambio, ignora este correo."
            }"""))
            .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON).body("""{"messageId":"msg-123"}"""))

        sender.sendPasswordReset("person@example.test", resetLink)
        server.verify()
    }

    @Test
    fun `registra estado HTTP y error de Brevo sin filtrar API key ni token`() {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        val apiKey = "brevo-api-secret"
        val resetToken = "private-reset-token-123456"
        val resetLink = "apunteia://reset-password?token=$resetToken"
        val sender = BrevoEmailService(builder, apiKey, "sender@example.test", "ApunteIA")
        val logger = LoggerFactory.getLogger(BrevoEmailService::class.java) as Logger
        val appender = ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>().apply { start() }
        logger.addAppender(appender)

        try {
            server.expect(requestTo("https://api.brevo.com/v3/smtp/email"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                    .body("""{"message":"invalid sender","debug":"$apiKey $resetLink token=$resetToken"}"""))

            val exception = runCatching { sender.sendPasswordReset("person@example.test", resetLink) }.exceptionOrNull()
            assertTrue(exception is EmailDeliveryException)
            val logs = appender.list.joinToString("\n") { it.formattedMessage }
            assertTrue(logs.contains("HTTP 400"))
            assertTrue(logs.contains("invalid sender"))
            assertFalse(logs.contains(apiKey))
            assertFalse(logs.contains(resetToken))
            assertFalse(logs.contains(resetLink))
            server.verify()
        } finally {
            logger.detachAppender(appender)
            appender.stop()
        }
    }
}
