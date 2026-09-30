package ApunteIA.BackEnd.entity

data class QuestionData(
    val text: String = "",
    val options: List<String> = emptyList(),
    val correctOptionIndex: Int = 0,
)

data class UserAnswerData(
    val questionIndex: Int = 0,
    val selectedOptionIndex: Int? = null,
    val correct: Boolean = false,
)