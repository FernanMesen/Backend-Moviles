package ApunteIA.BackEnd.ai

import ApunteIA.BackEnd.exception.AiProviderException
import org.springframework.http.MediaType
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import java.net.http.HttpClient
import java.time.Duration

/** Cliente del endpoint Chat Completions de OpenAI para generar resúmenes de notas. */
@Component
class AiClient(properties: AiProperties) {
    private val apiKey = properties.apiKey.trim()
    private val model = properties.model
    private val restClient: RestClient

    init {
        require(properties.timeoutSeconds > 0) { "app.ai.timeout-seconds debe ser mayor que cero" }

        val httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(properties.timeoutSeconds))
            .build()
        val requestFactory = JdkClientHttpRequestFactory(httpClient).apply {
            setReadTimeout(Duration.ofSeconds(properties.timeoutSeconds))
        }
        restClient = RestClient.builder()
            .baseUrl("https://api.openai.com/v1")
            .requestFactory(requestFactory)
            .defaultHeader("Authorization", "Bearer $apiKey")
            .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .build()
    }

    /** Envía el contenido al proveedor y devuelve el texto del resumen. */
    fun summarize(content: String): String {
        if (apiKey.isBlank()) {
            throw AiProviderException("El servicio de IA no está configurado (falta OPENAI_API_KEY)")
        }

        try {
            val response = restClient.post()
                .uri("/chat/completions")
                .body(
                    ChatCompletionRequest(
                        model = model,
                        messages = listOf(
                            ChatMessage("system", "Resume el contenido de apuntes en español. Sé claro, fiel al texto y conciso. Devuelve solo el resumen."),
                            ChatMessage("user", content),
                        ),
                    )
                )
                .retrieve()
                .body(ChatCompletionResponse::class.java)

            return response?.choices?.firstOrNull()?.message?.content?.trim()
                ?.takeIf(String::isNotEmpty)
                ?: throw AiProviderException("El proveedor de IA devolvió una respuesta vacía")
        } catch (ex: AiProviderException) {
            throw ex
        } catch (ex: RestClientException) {
            throw AiProviderException("No fue posible procesar la nota con el proveedor de IA", ex)
        }
    }

    private data class ChatCompletionRequest(
        val model: String,
        val messages: List<ChatMessage>,
    )

    private data class ChatMessage(
        val role: String,
        val content: String,
    )

    private data class ChatCompletionResponse(
        val choices: List<Choice> = emptyList(),
    )

    private data class Choice(
        val message: ResponseMessage? = null,
    )

    private data class ResponseMessage(
        val content: String? = null,
    )
}
