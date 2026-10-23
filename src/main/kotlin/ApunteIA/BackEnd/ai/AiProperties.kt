package ApunteIA.BackEnd.ai

import org.springframework.boot.context.properties.ConfigurationProperties

/** Configuración del proveedor de IA (`app.ai.*`). */
@ConfigurationProperties(prefix = "app.ai")
data class AiProperties(
    val apiKey: String = "",
    val model: String = "gpt-4o-mini",
    val timeoutSeconds: Long = 30,
)
