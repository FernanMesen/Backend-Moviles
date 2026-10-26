package ApunteIA.BackEnd.ai

import ApunteIA.BackEnd.exception.AiProviderException
import ApunteIA.BackEnd.exception.AiUnavailableException
import org.springframework.http.MediaType
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import java.net.http.HttpClient
import java.time.Duration

/** Implementación del contrato AiClient usando OpenAI Chat Completions. */
@Component
class OpenAiClient(properties: AiProperties) : AiClient {
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
            .baseUrl(properties.baseUrl.trimEnd('/'))
            .requestFactory(requestFactory)
            .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .build()
    }

    override fun complete(systemPrompt: String, userPrompt: String): String {
        if (apiKey.isBlank()) {
            throw AiUnavailableException("La IA no está configurada (falta AI_API_KEY)")
        }

        val response = try {
            restClient.post()
                .uri("/chat/completions")
                .header("Authorization", "Bearer $apiKey")
                .body(
                    ChatRequest(
                        model = model,
                        messages = listOf(
                            ChatMessage("system", systemPrompt),
                            ChatMessage("user", userPrompt),
                        ),
                    ),
                )
                .retrieve()
                .body(ChatResponse::class.java)
        } catch (ex: RestClientException) {
            throw AiProviderException("No fue posible procesar la nota con el proveedor de IA", ex)
        }

        return response?.choices?.firstOrNull()?.message?.content?.trim()?.takeIf(String::isNotEmpty)
            ?: throw AiProviderException("El proveedor de IA devolvió una respuesta vacía")
    }

    private data class ChatRequest(
        val model: String,
        val messages: List<ChatMessage>,
        val temperature: Double = 0.3,
    )

    private data class ChatMessage(
        val role: String,
        val content: String,
    )

    private data class ChatResponse(
        val choices: List<ChatChoice> = emptyList(),
    )

    private data class ChatChoice(
        val message: ResponseMessage? = null,
    )

    private data class ResponseMessage(
        val content: String? = null,
    )
}
