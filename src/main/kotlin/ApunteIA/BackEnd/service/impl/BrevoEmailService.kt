package ApunteIA.BackEnd.service.impl

import ApunteIA.BackEnd.exception.EmailDeliveryException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import org.springframework.web.client.ResourceAccessException

@Service
class BrevoEmailService(
    builder: RestClient.Builder,
    @param:Value("\${app.brevo.api-key}") private val apiKey: String,
    @param:Value("\${app.brevo.from-email}") private val fromEmail: String,
    @param:Value("\${app.brevo.from-name}") private val fromName: String,
) {
    private val client = builder.baseUrl("https://api.brevo.com/v3").build()
    private val logger = LoggerFactory.getLogger(javaClass)

    fun sendPasswordReset(to: String, resetLink: String) {
        if (apiKey.isBlank()) {
            logger.error("Brevo recovery is not configured: BREVO_API_KEY is missing")
            throw EmailDeliveryException()
        }
        if (fromEmail.isBlank()) {
            logger.error("Brevo recovery is not configured: BREVO_FROM_EMAIL is missing")
            throw EmailDeliveryException()
        }

        val request = mapOf(
            "sender" to mapOf("name" to fromName, "email" to fromEmail),
            "to" to listOf(mapOf("email" to to)),
            "subject" to "Recuperación de contraseña - ApunteIA",
            "textContent" to "Usa este enlace para restablecer tu contraseña. Vence en 30 minutos y solo puede usarse una vez:\n\n$resetLink\n\nSi no solicitaste este cambio, ignora este correo.",
        )

        try {
            val response = client.post()
                .uri("/smtp/email")
                .header("api-key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toBodilessEntity()
            logger.info("Brevo accepted password recovery email (HTTP {})", response.statusCode.value())
        } catch (ex: RestClientResponseException) {
            logger.error("Brevo rejected recovery email: HTTP {} response={}", ex.statusCode.value(), sanitize(ex.responseBodyAsString, resetLink))
            throw EmailDeliveryException()
        } catch (ex: ResourceAccessException) {
            logger.error("Brevo request failed before an HTTP response: {}", sanitize(ex.mostSpecificCause.message.orEmpty(), resetLink))
            throw EmailDeliveryException()
        }
    }

    private fun sanitize(value: String, resetLink: String): String {
        var safe = value
        if (apiKey.isNotBlank()) safe = safe.replace(apiKey, "[REDACTED_API_KEY]")
        if (resetLink.isNotBlank()) safe = safe.replace(resetLink, "[REDACTED_RESET_LINK]")
        safe = safe.replace(Regex("(?i)(token\\s*[=:]\\s*)[A-Za-z0-9._~-]{12,}"), "\$1[REDACTED]")
        return safe.replace(Regex("[\\r\\n\\t]+"), " ").take(500)
    }
}
