package ApunteIA.BackEnd.service.impl

import ApunteIA.BackEnd.dto.assessment.AssessmentResponse
import ApunteIA.BackEnd.dto.assessment.CreateAssessmentRequest
import ApunteIA.BackEnd.dto.assessment.CreateQuestionRequest
import ApunteIA.BackEnd.exception.BadRequestException
import ApunteIA.BackEnd.exception.ConflictException
import ApunteIA.BackEnd.exception.NotFoundException
import ApunteIA.BackEnd.mapper.AssessmentMapper
import ApunteIA.BackEnd.repository.AssessmentRepository
import ApunteIA.BackEnd.repository.CourseRepository
import ApunteIA.BackEnd.repository.UserRepository
import ApunteIA.BackEnd.service.AssessmentService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class AssessmentServiceImpl(
    private val assessmentRepository: AssessmentRepository,
    private val courseRepository: CourseRepository,
    private val userRepository: UserRepository,
    private val assessmentMapper: AssessmentMapper,
) : AssessmentService {

    override fun findByCourse(courseId: Long): List<AssessmentResponse> {
        if (!courseRepository.existsById(courseId)) {
            throw NotFoundException("No existe un curso con id $courseId")
        }
        return assessmentRepository.findAllByCourse_Id(courseId)
            .sortedBy { it.id }
            .map(assessmentMapper::toResponse)
    }

    override fun findById(id: Long): AssessmentResponse =
        assessmentMapper.toResponse(
            assessmentRepository.findById(id)
                .orElseThrow { NotFoundException("No existe una evaluación con id $id") }
        )

    @Transactional
    override fun create(courseId: Long, request: CreateAssessmentRequest): AssessmentResponse {
        validateQuestions(request.questions)
        val course = courseRepository.findById(courseId)
            .orElseThrow { NotFoundException("No existe un curso con id $courseId") }
        val createdBy = request.createdByUserId?.let { userId ->
            userRepository.findById(userId)
                .orElseThrow { NotFoundException("No existe un usuario con id $userId") }
        }
        val title = request.title.trim()
        if (assessmentRepository.existsByTitle(title)) {
            throw ConflictException("Ya existe una evaluación con el título $title")
        }
        val assessment = assessmentMapper.toEntity(request, course, createdBy)
        return assessmentMapper.toResponse(assessmentRepository.saveAndFlush(assessment))
    }

    private fun validateQuestions(questions: List<CreateQuestionRequest>) {
        if (questions.isEmpty()) throw BadRequestException("La evaluación debe tener al menos una pregunta")
        questions.forEachIndexed { index, question ->
            val number = index + 1
            if (question.text.isBlank()) {
                throw BadRequestException("La pregunta $number no tiene enunciado")
            }
            if (question.options.size < 2 || question.options.any { it.isBlank() }) {
                throw BadRequestException("La pregunta $number debe tener al menos 2 opciones no vacías")
            }
            val correct = question.correctOptionIndex
                ?: throw BadRequestException("La pregunta $number no tiene opción correcta")
            if (correct < 0 || correct >= question.options.size) {
                throw BadRequestException("La opción correcta de la pregunta $number está fuera de rango")
            }
        }
    }
}