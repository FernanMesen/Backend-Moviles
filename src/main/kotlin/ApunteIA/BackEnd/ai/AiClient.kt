package ApunteIA.BackEnd.ai

/** Contrato compartido para generar texto con IA desde el backend. */
interface AiClient {
    /** Envía las instrucciones y el contenido del usuario; devuelve la respuesta del modelo. */
    fun complete(systemPrompt: String, userPrompt: String): String
}
