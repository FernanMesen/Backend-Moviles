package ApunteIA.BackEnd.ai

import ApunteIA.BackEnd.exception.AiUnavailableException
import ApunteIA.BackEnd.exception.AiProviderException
import org.junit.jupiter.api.Test
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import kotlin.test.assertFailsWith

class OpenAiClientTest {
    @Test
    fun `missing API key fails before making a provider request`() {
        val client = OpenAiClient(AiProperties(apiKey = " "))

        assertFailsWith<AiUnavailableException> {
            client.complete("system", "user")
        }
    }

    @Test
    fun `provider HTTP error is translated to AiProviderException`() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/v1/chat/completions") { exchange ->
            val body = """{"error":{"message":"simulated quota exceeded"}}""".toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(429, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()

        try {
            val client = OpenAiClient(
                AiProperties(
                    apiKey = "test-api-key",
                    baseUrl = "http://127.0.0.1:${server.address.port}/v1",
                ),
            )

            assertFailsWith<AiProviderException> {
                client.complete("system", "user")
            }
        } finally {
            server.stop(0)
        }
    }
}
