import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class TestResponse(
    val message: String,
    val status: String
)

fun main() {
    embeddedServer(
        factory = Netty,
        host = "0.0.0.0",
        port = 8080
    ) {
        configureSerialization()
        configureRouting()
    }.start(wait = true)
}

fun Application.configureSerialization() {
    install(ContentNegotiation) {
        json()
    }
}

fun Application.configureRouting() {

    routing {

        get("/") {
            call.respondText(
                text = "Backend ApuntelA funcionando",
                contentType = ContentType.Text.Plain,
                status = HttpStatusCode.OK
            )
        }

        get("/api/test") {
            call.respond(
                TestResponse(
                    message = "Backend funcionando correctamente",
                    status = "OK"
                )
            )
        }
    }
}