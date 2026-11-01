package ApunteIA.BackEnd.ai

import ApunteIA.BackEnd.exception.BadRequestException

enum class AiLanguage(val code: String, val displayName: String, val instruction: String) {
    ES("es", "Español", "Responde en español."),
    EN("en", "English", "Respond in English."),
    PT("pt", "Português", "Responda em português."),
    IT("it", "Italiano", "Rispondi in italiano.");

    companion object {
        fun resolve(code: String?): AiLanguage = entries.firstOrNull { it.code == code }
            ?: if (code == null) ES else throw BadRequestException("Idioma de IA inválido: '$code'. Valores permitidos: es, en, pt, it")
    }
}
