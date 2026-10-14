package ApunteIA.BackEnd.mapper

import ApunteIA.BackEnd.dto.attempt.AttemptAnswerResponse
import ApunteIA.BackEnd.dto.attempt.AttemptResponse
import ApunteIA.BackEnd.entity.AssessmentAttempt
import ApunteIA.BackEnd.entity.AttemptStatus
import org.springframework.stereotype.Component

/** Se utiliza dentro de un servicio @Transactional para acceder a las relaciones LAZY. */
@Component
class AttemptMapper {
    fun toResponse(attempt: AssessmentAttempt) = AttemptResponse(
        id = requireNotNull(attempt.id) { "El intento no ha sido guardado" },
        assessmentId = requireNotNull(attempt.assessment.id) { "La evaluación del intento no tiene id" },
        assessmentTitle = attempt.assessment.title,
        userId = requireNotNull(attempt.user.id) { "El usuario del intento no tiene id" },
        status = if (attempt.completed) AttemptStatus.COMPLETED else AttemptStatus.IN_PROGRESS,
        score = attempt.score,
        startedAt = attempt.startedAt,
        completedAt = attempt.completedAt,
        durationSeconds = attempt.durationSeconds,
        answers = attempt.userAnswers.map {
            AttemptAnswerResponse(
                questionIndex = it.questionIndex,
                selectedOptionIndex = it.selectedOptionIndex,
                correct = it.correct,
            )
        },
    )
}