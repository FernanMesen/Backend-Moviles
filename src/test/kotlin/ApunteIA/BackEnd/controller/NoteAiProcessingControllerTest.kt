package ApunteIA.BackEnd.controller

import ApunteIA.BackEnd.ai.AiClient
import ApunteIA.BackEnd.entity.Course
import ApunteIA.BackEnd.entity.Note
import ApunteIA.BackEnd.entity.ProcessingStatus
import ApunteIA.BackEnd.exception.AiProviderException
import ApunteIA.BackEnd.exception.AiUnavailableException
import ApunteIA.BackEnd.repository.CourseRepository
import ApunteIA.BackEnd.repository.NoteRepository
import ApunteIA.BackEnd.repository.UserRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.reset
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(roles = ["ADMIN"])
@TestPropertySource(properties = ["spring.datasource.url=jdbc:h2:mem:apunteia-note-ai-web;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE"])
class NoteAiProcessingControllerTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val noteRepository: NoteRepository,
    private val courseRepository: CourseRepository,
    private val userRepository: UserRepository,
) {
    @MockitoBean
    private lateinit var aiClient: AiClient

    private val user by lazy { userRepository.findByEmail("demo@apunteia.com")!! }
    private val course by lazy { courseRepository.findByCode("EIF411")!! }

    @BeforeEach
    fun resetAiClient() {
        reset(aiClient)
    }

    @Test
    fun `missing AI key returns 503 and marks the note FAILED`() {
        val note = createNote()
        doThrow(AiUnavailableException("La IA no está configurada (falta AI_API_KEY)"))
            .`when`(aiClient).complete(anyString(), anyString())

        mockMvc.post("/notes/${note.id}/process").andExpect {
            status { isServiceUnavailable() }
            jsonPath("$.status") { value(503) }
        }

        assertNoteFailed(note.id!!)
    }

    @Test
    fun `OpenAI provider error returns 502 and marks the note FAILED`() {
        val note = createNote()
        doThrow(AiProviderException("No fue posible procesar la nota con el proveedor de IA"))
            .`when`(aiClient).complete(anyString(), anyString())

        mockMvc.post("/notes/${note.id}/process").andExpect {
            status { isBadGateway() }
            jsonPath("$.status") { value(502) }
        }

        assertNoteFailed(note.id!!)
    }

    @Test
    fun `processing a note already in PROCESSING returns 409 without calling AI`() {
        val note = createNote(ProcessingStatus.PROCESSING)

        mockMvc.post("/notes/${note.id}/process").andExpect {
            status { isConflict() }
            jsonPath("$.status") { value(409) }
        }

        verifyNoInteractions(aiClient)
        assertStatus(note.id!!, ProcessingStatus.PROCESSING)
    }

    @Test
    fun `processing a note in FAILED returns 409 without calling AI`() {
        val note = createNote(ProcessingStatus.FAILED)

        mockMvc.post("/notes/${note.id}/process").andExpect {
            status { isConflict() }
            jsonPath("$.status") { value(409) }
        }

        verifyNoInteractions(aiClient)
        assertStatus(note.id!!, ProcessingStatus.FAILED)
    }

    private fun createNote(status: ProcessingStatus = ProcessingStatus.PENDING) =
        noteRepository.saveAndFlush(
            Note(
                title = "Prueba IA ${UUID.randomUUID()}",
                content = "Contenido de prueba para el procesamiento con IA.",
                user = user,
                course = course,
                processingStatus = status,
            ),
        )

    private fun assertNoteFailed(id: Long) {
        mockMvc.get("/notes/$id").andExpect {
            status { isOk() }
            jsonPath("$.processingStatus") { value("FAILED") }
            jsonPath("$.summary") { doesNotExist() }
        }
    }

    private fun assertStatus(id: Long, status: ProcessingStatus) {
        mockMvc.get("/notes/$id").andExpect {
            status { isOk() }
            jsonPath("$.processingStatus") { value(status.name) }
        }
    }
}
