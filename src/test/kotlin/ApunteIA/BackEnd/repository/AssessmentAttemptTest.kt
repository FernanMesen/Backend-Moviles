package ApunteIA.BackEnd.repository

import ApunteIA.BackEnd.entity.Assessment
import ApunteIA.BackEnd.entity.AssessmentAttempt
import ApunteIA.BackEnd.entity.Privilege
import ApunteIA.BackEnd.entity.QuestionData
import ApunteIA.BackEnd.entity.Role
import ApunteIA.BackEnd.entity.User
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Pruebas unitarias del cálculo de puntaje y del registro de intentos. */
class AssessmentAttemptTest {
    private val assessment = Assessment(
        title = "Introducción al Backend",
        questions = listOf(
            QuestionData("P1", listOf("a", "b", "c"), 1),
            QuestionData("P2", listOf("a", "b", "c"), 2),
            QuestionData("P3", listOf("a", "b", "c"), 0),
        ),
        id = 1L,
    )

    private val user = User(
        email = "estudiante@apunteia.com",
        fullName = "Estudiante de Prueba",
        role = Role(Role.STUDENT, "Estudiante"),
        privilege = Privilege(Privilege.BASIC, "Acceso básico"),
    )

    private fun scoreOf(selected: List<Int?>) = AssessmentAttempt.score(assessment.grade(selected))

    @Test
    fun `todas las respuestas correctas dan 100`() {
        assertEquals(BigDecimal("100.00"), scoreOf(listOf(1, 2, 0)))
    }

    @Test
    fun `todas las respuestas incorrectas dan 0`() {
        assertEquals(BigDecimal("0.00"), scoreOf(listOf(0, 0, 1)))
    }

    @Test
    fun `puntaje parcial se redondea a dos decimales`() {
        assertEquals(BigDecimal("66.67"), scoreOf(listOf(1, 2, 2)))
    }

    @Test
    fun `pregunta sin responder cuenta como incorrecta`() {
        val answers = assessment.grade(listOf(1, null, 0))
        assertFalse(answers[1].correct)
        assertEquals(BigDecimal("66.67"), AssessmentAttempt.score(answers))
    }

    @Test
    fun `sin respuestas el puntaje es 0`() {
        assertEquals(BigDecimal("0.00"), AssessmentAttempt.score(emptyList()))
    }

    @Test
    fun `grade conserva indice de pregunta y opcion elegida`() {
        val answers = assessment.grade(listOf(1, 0, 0))
        assertEquals(listOf(0, 1, 2), answers.map { it.questionIndex })
        assertEquals(listOf(1, 0, 0), answers.map { it.selectedOptionIndex })
        assertTrue(answers[0].correct)
        assertFalse(answers[1].correct)
    }

    @Test
    fun `grade falla si la cantidad de respuestas no coincide con las preguntas`() {
        assertThrows<IllegalArgumentException> { assessment.grade(listOf(1, 2)) }
    }

    @Test
    fun `register arma el intento con puntaje, respuestas y tiempos`() {
        val completedAt = Instant.parse("2026-09-30T12:00:00Z")

        val attempt = AssessmentAttempt.register(assessment, user, listOf(1, 2, 2), 300L, completedAt)

        assertSame(assessment, attempt.assessment)
        assertSame(user, attempt.user)
        assertEquals(BigDecimal("66.67"), attempt.score)
        assertEquals(300L, attempt.durationSeconds)
        assertEquals(completedAt, attempt.completedAt)
        assertEquals(Instant.parse("2026-09-30T11:55:00Z"), attempt.startedAt)
        assertEquals(listOf(true, true, false), attempt.userAnswers.map { it.correct })
    }

    @Test
    fun `register falla si la cantidad de respuestas no coincide`() {
        assertThrows<IllegalArgumentException> {
            AssessmentAttempt.register(assessment, user, listOf(1, 2), 60L)
        }
    }
}