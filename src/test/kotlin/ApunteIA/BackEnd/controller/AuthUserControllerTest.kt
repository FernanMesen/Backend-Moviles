package ApunteIA.BackEnd.controller

import org.hamcrest.Matchers.everyItem
import org.hamcrest.Matchers.`is`
import org.hamcrest.Matchers.startsWith
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import java.util.UUID

/**
 * Pruebas de integración de la API de Autenticación y Usuarios (Lab 6).
 * Levanta la app completa sobre H2 en memoria, con los datos de prueba del IdentitySeeder,
 * y hace peticiones HTTP simuladas con MockMvc. Cubre los mismos casos que la colección de Postman.
 */
@SpringBootTest
@AutoConfigureMockMvc
// Estas pruebas validan la lógica de cada endpoint; la seguridad JWT se prueba en SecurityJwtTest
@WithMockUser(roles = ["ADMIN"])
@TestPropertySource(properties = ["spring.datasource.url=jdbc:h2:mem:apunteia-web;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE"])
class AuthUserControllerTest @Autowired constructor(
    private val mockMvc: MockMvc,
) {
    private fun uniqueEmail() = "test-${UUID.randomUUID()}@apunteia.com"

    private fun register(email: String) = mockMvc.post("/auth/register") {
        contentType = MediaType.APPLICATION_JSON
        content = """{"fullName":"Prueba","email":"$email","password":"123456"}"""
    }

    // ---------- POST /auth/login ----------

    @Test
    fun `login correcto devuelve 200 con token y userName`() {
        mockMvc.post("/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"demo@apunteia.com","password":"123456"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.token") { value(startsWith("eyJ")) } // JWT: header.payload.firma
            jsonPath("$.tokenType") { value("Bearer") }
            jsonPath("$.userName") { value("Maya Rodríguez") }
        }
    }

    @Test
    fun `login con contraseña incorrecta devuelve 401`() {
        mockMvc.post("/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"demo@apunteia.com","password":"mala"}"""
        }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.message") { value("Credenciales inválidas") }
        }
    }

    @Test
    fun `login de usuario bloqueado devuelve 403`() {
        mockMvc.post("/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"bloqueado@apunteia.com","password":"123456"}"""
        }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `login con email inválido devuelve 400 con el campo que falló`() {
        mockMvc.post("/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"no-es-email","password":""}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.fieldErrors.email") { exists() }
            jsonPath("$.fieldErrors.password") { exists() }
        }
    }

    @Test
    fun `login con email sin cuenta devuelve 401 y mensaje descriptivo`() {
        mockMvc.post("/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"inexistente-${UUID.randomUUID()}@apunteia.com","password":"123456"}"""
        }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.status") { value(401) }
            jsonPath("$.message") { value("No existe una cuenta registrada con este correo.") }
        }
    }

    // ---------- POST /auth/register ----------

    @Test
    fun `registro correcto devuelve 201 sin password`() {
        register(uniqueEmail()).andExpect {
            status { isCreated() }
            header { string("Location", org.hamcrest.Matchers.containsString("/users/")) }
            jsonPath("$.role") { value("STUDENT") }
            jsonPath("$.status") { value("ACTIVE") }
            jsonPath("$.passwordHash") { doesNotExist() }
        }
    }

    @Test
    fun `registro con email duplicado devuelve 409`() {
        register("demo@apunteia.com").andExpect { status { isConflict() } }
    }

    // ---------- /users ----------

    @Test
    fun `GET users filtra por estado`() {
        mockMvc.get("/users?status=BLOCKED").andExpect {
            status { isOk() }
            jsonPath("$[*].status") { value(everyItem(`is`("BLOCKED"))) }
        }
    }

    @Test
    fun `GET users con id inexistente devuelve 404 y con id no numérico 400`() {
        mockMvc.get("/users/999999").andExpect { status { isNotFound() } }
        mockMvc.get("/users/abc").andExpect { status { isBadRequest() } }
    }

    @Test
    fun `bloquear, eliminar y no poder reactivar un usuario`() {
        val email = uniqueEmail()
        val body = register(email).andReturn().response.contentAsString
        val id = Regex("\"id\":(\\d+)").find(body)!!.groupValues[1]

        fun patch(status: String) = mockMvc.patch("/users/$id/status") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"status":"$status"}"""
        }

        patch("BLOCKED").andExpect { status { isOk() }; jsonPath("$.status") { value("BLOCKED") } }
        patch("DELETED").andExpect { status { isOk() } }
        patch("ACTIVE").andExpect { status { isConflict() } }
        patch("NO_EXISTE").andExpect { status { isBadRequest() } }
    }
}
