package ApunteIA.BackEnd.mapper

import ApunteIA.BackEnd.dto.assessment.AssessmentResponse
import ApunteIA.BackEnd.dto.assessment.CreateAssessmentRequest
import ApunteIA.BackEnd.dto.assessment.QuestionResponse
import ApunteIA.BackEnd.entity.Assessment
import ApunteIA.BackEnd.entity.Course
import ApunteIA.BackEnd.entity.QuestionData
import ApunteIA.BackEnd.entity.User
import org.springframework.stereotype.Component

/** Se utiliza dentro de un servicio @Transactional para acceder a las relaciones LAZY. */
@Component
class AssessmentMapper {
    fun toEntity(request: CreateAssessmentRequest, course: Course, createdBy: User?) = Assessment(
        title = request.title.trim(),
        description = request.description?.trim(),
        durationMinutes = request.durationMinutes,
        type = request.type,
        difficulty = request.difficulty,
        course = course,
        createdBy = createdBy,
        questions = request.questions.map { question ->
            QuestionData(
                text = question.text.trim(),
                options = question.options.map { it.trim() },
                correctOptionIndex = requireNotNull(question.correctOptionIndex) { "La pregunta no tiene opción correcta" },
            )
        },
    )

    fun toResponse(assessment: Assessment) = AssessmentResponse(
        id = requireNotNull(assessment.id) { "La evaluación no ha sido guardada" },
        title = assessment.title,
        description = assessment.description,
        type = assessment.type,
        difficulty = assessment.difficulty,
        durationMinutes = assessment.durationMinutes,
        courseId = assessment.course?.id,
        courseName = assessment.course?.name,
        createdByUserId = assessment.createdBy?.id,
        totalQuestions = assessment.questions.size,
        questions = assessment.questions.mapIndexed { index, question ->
            QuestionResponse(index = index, text = question.text, options = question.options)
        },
    )
}