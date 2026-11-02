package ApunteIA.BackEnd.ai

import ApunteIA.BackEnd.entity.AssessmentDifficulty
import ApunteIA.BackEnd.service.impl.NoteServiceImpl
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AiLanguagePromptTest {
    @Test
    fun `summary and assessment prompts enforce each requested output language`() {
        val expected = mapOf(
            AiLanguage.ES to "Spanish",
            AiLanguage.EN to "English",
            AiLanguage.PT to "Portuguese",
            AiLanguage.IT to "Italian",
        )

        expected.forEach { (language, name) ->
            val summaryPrompt = NoteServiceImpl.systemPrompt(language)
            val assessmentPrompt = AssessmentPrompt.system(2, AssessmentDifficulty.MEDIUM, language)

            assertContains(summaryPrompt, "Generate the entire response only in $name")
            assertContains(assessmentPrompt, "Generate the entire response only in $name")
            assertContains(summaryPrompt, "source text or OCR language does not determine the output language")
            assertContains(assessmentPrompt, "source text or OCR language does not determine the output language")
            assertTrue(summaryPrompt.indexOf(language.instruction) < summaryPrompt.indexOf(NoteServiceImpl.SYSTEM_PROMPT))
            assertTrue(summaryPrompt.lastIndexOf(language.instruction) > summaryPrompt.indexOf(NoteServiceImpl.SYSTEM_PROMPT))
        }

        listOf(AiLanguage.EN, AiLanguage.PT, AiLanguage.IT).forEach { language ->
            assertContains(language.instruction, "Do not answer in Spanish")
            assertContains(AssessmentPrompt.system(2, AssessmentDifficulty.MEDIUM, language), "Do not answer in Spanish")
            assertContains(NoteServiceImpl.systemPrompt(language), "Do not answer in Spanish")
        }
        assertFalse(AiLanguage.resolve(null) != AiLanguage.ES)
    }
}
