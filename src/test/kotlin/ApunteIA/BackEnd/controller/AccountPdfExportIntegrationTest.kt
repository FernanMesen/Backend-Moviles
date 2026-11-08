package ApunteIA.BackEnd.controller

import ApunteIA.BackEnd.entity.Note
import ApunteIA.BackEnd.entity.ProcessingStatus
import ApunteIA.BackEnd.repository.CourseRepository
import ApunteIA.BackEnd.repository.NoteRepository
import ApunteIA.BackEnd.repository.UserRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.transaction.support.TransactionTemplate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = ["spring.datasource.url=jdbc:h2:mem:apunteia-pdf-export;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE"])
class AccountPdfExportIntegrationTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val objectMapper: ObjectMapper,
    private val userRepository: UserRepository,
    private val noteRepository: NoteRepository,
    private val courseRepository: CourseRepository,
    private val transactionTemplate: TransactionTemplate,
) {
    private fun login(email: String, password: String): String {
        val response = mockMvc.post("/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"$email","password":"$password"}"""
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString
        return objectMapper.readTree(response)["token"].asText()
    }

    @Test
    fun `PDF de cuenta vacia y PDF extenso son archivos validos y completos`() {
        val sparseEmail = "sparse-pdf-${System.nanoTime()}@apunteia.com"
        mockMvc.post("/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"fullName":"PDF Vacío","email":"$sparseEmail","password":"123456"}"""
        }.andExpect { status { isCreated() } }

        val sparsePdf = downloadPdf(login(sparseEmail, "123456"))
        assertPdfParses(sparsePdf, minimumPages = 1)

        val richUser = requireNotNull(userRepository.findByEmail("demo@apunteia.com"))
        val course = requireNotNull(courseRepository.findByCode("EIF411"))
        val finalMarker = "FIN-DE-CONTENIDO-EXTENSO"
        val longBody = ("Texto largo con acentos: información, evaluación, año. " +
            "Símbolos: € © — • y emoji 🚀.\n").repeat(250) + finalMarker
        transactionTemplate.executeWithoutResult {
            noteRepository.saveAndFlush(Note(
                title = "Nota extensa y Unicode",
                content = longBody,
                summary = null,
                user = richUser,
                course = course,
                processingStatus = ProcessingStatus.COMPLETED,
            ))
        }

        val richPdf = downloadPdf(login("demo@apunteia.com", "123456"))
        assertTrue(richPdf.size > sparsePdf.size, "El PDF con más datos debe contener más bytes")
        val extracted = assertPdfParses(richPdf, minimumPages = 2)
        assertTrue(extracted.contains(finalMarker), "El PDF debe conservar el final del texto largo")
        assertTrue(extracted.contains("informaci"), "El texto y los caracteres compatibles deben extraerse")
    }

    private fun downloadPdf(token: String): ByteArray {
        val response = mockMvc.get("/users/me/export?format=pdf") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            accept = MediaType.APPLICATION_PDF
        }.andExpect {
            status { isOk() }
            header { string(HttpHeaders.CONTENT_TYPE, "application/pdf") }
            header { string(HttpHeaders.CONTENT_DISPOSITION, org.hamcrest.Matchers.containsString("attachment")) }
        }.andReturn().response

        val bytes = response.contentAsByteArray
        assertTrue(bytes.size > 100, "La respuesta PDF no debe estar vacía")
        assertTrue(bytes.copyOfRange(0, 5).contentEquals("%PDF-".toByteArray()), "Debe tener la firma PDF")
        assertEquals(bytes.size.toLong(), response.getHeader(HttpHeaders.CONTENT_LENGTH)?.toLong())
        return bytes
    }

    private fun assertPdfParses(bytes: ByteArray, minimumPages: Int): String = Loader.loadPDF(bytes).use { document ->
        assertTrue(document.numberOfPages >= minimumPages)
        PDFTextStripper().getText(document)
    }
}
