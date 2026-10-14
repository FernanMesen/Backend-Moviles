package ApunteIA.BackEnd.service.impl

import ApunteIA.BackEnd.dto.attempt.AttemptResponse
import ApunteIA.BackEnd.dto.attempt.StartAttemptRequest
import ApunteIA.BackEnd.dto.attempt.SubmitAttemptRequest
import ApunteIA.BackEnd.entity.AssessmentAttempt
import ApunteIA.BackEnd.exception.BadRequestException
import ApunteIA.BackEnd.exception.ConflictException
import ApunteIA.BackEnd.exception.NotFoundException
import ApunteIA.BackEnd.mapper.AttemptMapper
import ApunteIA.BackEnd.repository.AssessmentAttemptRepository
import ApunteIA.BackEnd.repository.AssessmentRepository
import ApunteIA.BackEnd.repository.UserRepository
import ApunteIA.BackEnd.service.AttemptService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant

@Service
@Transactional(readOnly = true)
class AttemptServiceImpl(
    private val attemptRepository: AssessmentAttemptRepository,
    private val assessmentRepository: AssessmentRepository,
    private val userRepository: UserRepository,
    private val attemptMapper: AttemptMapper,
) : AttemptService {

    override fun findById(id: Long): AttemptResponse = attemptMapper.toResponse(findAttemptOrThrow(id))

    override fun findByUser(userId: Long): List<AttemptResponse> {
        if (!userRepository.existsById(userId)) {
            throw NotFoundException("No existe un usuario con id $userId")
        }
        return attemptRepository.findAllByUser_IdOrderByStartedAtDesc(userId).map(attemptMapper::toResponse)
    }

    @Transactional
    override fun start(assessmentId: Long, request: StartAttemptRequest): AttemptResponse {
        val userId = request.userId ?: throw BadRequestException("El usuario es obligatorio")
        if (userId <= 0) throw BadRequestException("El id del usuario debe ser positivo")

        val assessment = assessmentRepository.findById(assessmentId)
            .orElseThrow { NotFoundException("No existe una evaluación con id $assessmentId") }
        val user = userRepository.findById(userId)
            .orElseThrow { NotFoundException("No existe un usuario con id $userId") }
        if (attemptRepository.existsByUser_IdAndAssessment_IdAndCompletedAtIsNull(userId, assessmentId)) {
            throw ConflictException("El usuario $userId ya tiene un intento en progreso de la evaluación $assessmentId")
        }
        val attempt = AssessmentAttempt(assessment = assessment, user = user)
        return attemptMapper.toResponse(attemptRepository.saveAndFlush(attempt))
    }

    @Transactional
    override fun submit(attemptId: Long, request: SubmitAttemptRequest): AttemptResponse {
        val selected = request.answers ?: throw BadRequestException("Las respuestas son obligatorias")
        val attempt = findAttemptOrThrow(attemptId)
        if (attempt.completed) {
            throw ConflictException("El intento $attemptId ya fue completado")
        }

        val questions = attempt.assessment.questions
        if (selected.size != questions.size) {
            throw BadRequestException("Se esperaban ${questions.size} respuestas y se recibieron ${selected.size}")
        }
        selected.forEachIndexed { index, option ->
            if (option != null && option !in questions[index].options.indices) {
                throw BadRequestException("La respuesta ${index + 1} está fuera del rango de opciones")
            }
        }

        val answers = attempt.assessment.grade(selected)
        val completedAt = Instant.now()
        attempt.userAnswers = answers
        attempt.score = AssessmentAttempt.score(answers)
        attempt.completedAt = completedAt
        attempt.durationSeconds = Duration.between(attempt.startedAt, completedAt).seconds.coerceAtLeast(0)
        return attemptMapper.toResponse(attemptRepository.saveAndFlush(attempt))
    }

    private fun findAttemptOrThrow(id: Long) = attemptRepository.findById(id)
        .orElseThrow { NotFoundException("No existe un intento con id $id") }
}