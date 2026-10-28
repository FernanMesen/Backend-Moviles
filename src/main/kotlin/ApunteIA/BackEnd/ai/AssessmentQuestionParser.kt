package ApunteIA.BackEnd.ai

import ApunteIA.BackEnd.entity.QuestionData
import ApunteIA.BackEnd.exception.AiProviderException
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component

@Component
class AssessmentQuestionParser {
    private val objectMapper = ObjectMapper()

    fun parse(rawResponse: String, expectedCount: Int): List<QuestionData> {
        val root = readJson(rawResponse)
        val questions = when {
            root.isArray -> root
            root.path("questions").isArray -> root.path("questions")
            else -> throw invalid("falta el arreglo \"questions\"")
        }
        if (questions.size() < expectedCount) {
            throw invalid("se pidieron $expectedCount preguntas y llegaron ${questions.size()}")
        }
        return questions.take(expectedCount).mapIndexed { index, node -> toQuestion(node, index + 1) }
    }

    private fun readJson(rawResponse: String): JsonNode {
        val start = rawResponse.indexOfFirst { it == '{' || it == '[' }
        val end = rawResponse.indexOfLast { it == '}' || it == ']' }
        if (start == -1 || end <= start) throw invalid("la respuesta no contiene JSON")
        return try {
            objectMapper.readTree(rawResponse.substring(start, end + 1))
        } catch (ex: JsonProcessingException) {
            throw invalid("la respuesta no es un JSON válido")
        }
    }

    private fun toQuestion(node: JsonNode, number: Int): QuestionData {
        val text = node.path("text").takeIf { it.isTextual }?.asText()?.trim().orEmpty()
        if (text.isEmpty()) throw invalid("la pregunta $number no tiene enunciado")

        val optionsNode = node.path("options")
        if (!optionsNode.isArray || optionsNode.any { !it.isTextual || it.asText().isBlank() }) {
            throw invalid("la pregunta $number tiene opciones inválidas")
        }
        val options = optionsNode.map { it.asText().trim() }
        if (options.size !in MIN_OPTIONS..MAX_OPTIONS) {
            throw invalid("la pregunta $number debe tener entre $MIN_OPTIONS y $MAX_OPTIONS opciones")
        }
        if (options.distinct().size != options.size) {
            throw invalid("la pregunta $number tiene opciones repetidas")
        }

        val correct = node.path("correctOptionIndex")
        if (!correct.isInt) throw invalid("la pregunta $number no tiene un correctOptionIndex numérico")
        if (correct.asInt() !in options.indices) {
            throw invalid("la opción correcta de la pregunta $number está fuera de rango")
        }
        return QuestionData(text = text, options = options, correctOptionIndex = correct.asInt())
    }

    private fun invalid(reason: String) =
        AiProviderException("La respuesta de la IA no tiene el formato esperado: $reason")

    private companion object {
        const val MIN_OPTIONS = 2
        const val MAX_OPTIONS = 10
    }
}