package ApunteIA.BackEnd.ai

import ApunteIA.BackEnd.exception.BadRequestException

enum class AiLanguage(val code: String, val displayName: String, val instruction: String) {
    ES("es", "Español", "IMPORTANT OUTPUT LANGUAGE RULE: Generate the entire response only in Spanish. The source text or OCR language does not determine the output language. All titles, headings, bullets, explanations, questions, and options must be in Spanish."),
    EN("en", "English", "IMPORTANT OUTPUT LANGUAGE RULE: Generate the entire response only in English. Do not answer in Spanish, even if the source text or OCR is in Spanish. The source text or OCR language does not determine the output language. All titles, headings, bullets, explanations, questions, and options must be in English."),
    PT("pt", "Português", "IMPORTANT OUTPUT LANGUAGE RULE: Generate the entire response only in Portuguese. Do not answer in Spanish, even if the source text or OCR is in Spanish. The source text or OCR language does not determine the output language. All titles, headings, bullets, explanations, questions, and options must be in Portuguese."),
    IT("it", "Italiano", "IMPORTANT OUTPUT LANGUAGE RULE: Generate the entire response only in Italian. Do not answer in Spanish, even if the source text or OCR is in Spanish. The source text or OCR language does not determine the output language. All titles, headings, bullets, explanations, questions, and options must be in Italian.");

    companion object {
        fun resolve(code: String?): AiLanguage = entries.firstOrNull { it.code == code }
            ?: if (code == null) ES else throw BadRequestException("Idioma de IA inválido: '$code'. Valores permitidos: es, en, pt, it")
    }
}
