package ApunteIA.BackEnd.service.impl

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException

@Service
class BrevoEmailService(
    builder: RestClient.Builder,
    @param:Value("\${app.brevo.api-key}") private val apiKey: String,
    @param:Value("\${app.brevo.from-email}") private val fromEmail: String,
    @param:Value("\${app.brevo.from-name}") private val fromName: String,
) {
    private val client = builder.baseUrl("https://api.brevo.com/v3").build()

    fun sendPasswordReset(to: String, resetLink: String) {
        check(apiKey.isNotBlank()) { "Falta configurar BREVO_API_KEY en el entorno del backend" }
        check(fromEmail.isNotBlank()) { "Falta configurar BREVO_FROM_EMAIL con un remitente verificado en Brevo" }

        val request = mapOf(
            "sender" to mapOf("name" to fromName, "email" to fromEmail),
            "to" to listOf(mapOf("email" to to)),
            "subject" to "Recuperación de contraseña - ApunteIA",
            "textContent" to "Usa este enlace para restablecer tu contraseña. Vence en 30 minutos y solo puede usarse una vez:\n\n$resetLink\n\nSi no solicitaste este cambio, ignora este correo.",
        )

        try {
            client.post()
                .uri("/smtp/email")
                .header("api-key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toBodilessEntity()
        } catch (ex: RestClientResponseException) {
            throw IllegalStateException("Brevo rechazó el envío (HTTP ${ex.statusCode.value()}): ${ex.responseBodyAsString}", ex)
        }
    }
}
