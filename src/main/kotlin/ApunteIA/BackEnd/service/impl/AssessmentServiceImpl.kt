package ApunteIA.BackEnd.service.impl

import ApunteIA.BackEnd.ai.AiClient
import ApunteIA.BackEnd.ai.AiLanguage
import ApunteIA.BackEnd.ai.AssessmentPrompt
import ApunteIA.BackEnd.ai.AssessmentQuestionParser
import ApunteIA.BackEnd.dto.assessment.AssessmentResponse
import ApunteIA.BackEnd.dto.assessment.CreateAssessmentRequest
import ApunteIA.BackEnd.dto.assessment.CreateQuestionRequest
import ApunteIA.BackEnd.dto.assessment.GenerateAssessmentRequest
import ApunteIA.BackEnd.entity.Assessment
import ApunteIA.BackEnd.entity.AssessmentDifficulty
import ApunteIA.BackEnd.entity.AssessmentType
import ApunteIA.BackEnd.entity.QuestionData
import ApunteIA.BackEnd.exception.BadRequestException
import ApunteIA.BackEnd.exception.ConflictException
import ApunteIA.BackEnd.exception.NotFoundException
import ApunteIA.BackEnd.mapper.AssessmentMapper
import ApunteIA.BackEnd.repository.AssessmentRepository
import ApunteIA.BackEnd.repository.CourseRepository
import ApunteIA.BackEnd.repository.NoteRepository
import ApunteIA.BackEnd.repository.UserRepository
import ApunteIA.BackEnd.service.AssessmentService
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate

@Service
@Transactional(readOnly = true)
class AssessmentServiceImpl(
    private val assessmentRepository: AssessmentRepository,
    private val courseRepository: CourseRepository,
    private val userRepository: UserRepository,
    private val noteRepository: NoteRepository,
    private val assessmentMapper: AssessmentMapper,
    private val aiClient: AiClient,
    private val questionParser: AssessmentQuestionParser,
    transactionManager: PlatformTransactionManager,
) : AssessmentService {

    private val transactions = TransactionTemplate(transactionManager)

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

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    override fun generateFromNote(noteId: Long, creatorId: Long, request: GenerateAssessmentRequest): AssessmentResponse {
        val questionCount = requireNotNull(request.questionCount) { "La cantidad de preguntas es obligatoria" }
        val language = AiLanguage.resolve(request.language)
        val source = transactions.execute { loadSource(noteId) }!!
        val aiResponse = aiClient.complete(
            AssessmentPrompt.system(questionCount, request.difficulty, language),
            AssessmentPrompt.user(source.noteTitle, source.content),
        )
        val questions = questionParser.parse(aiResponse, questionCount)
        return transactions.execute { saveGenerated(source, creatorId, request.difficulty, questions) }!!
    }

    private fun loadSource(noteId: Long): GenerationSource {
        val note = noteRepository.findById(noteId)
            .orElseThrow { NotFoundException("No existe una nota con id $noteId") }
        if (note.content.isBlank()) {
            throw BadRequestException("La nota no tiene contenido para generar preguntas")
        }
        return GenerationSource(
            courseId = requireNotNull(note.course.id) { "La nota no tiene curso" },
            noteTitle = note.title,
            content = note.content,
        )
    }

    private fun saveGenerated(
        source: GenerationSource,
        creatorId: Long,
        difficulty: AssessmentDifficulty,
        questions: List<QuestionData>,
    ): AssessmentResponse {
        val course = courseRepository.findById(source.courseId)
            .orElseThrow { NotFoundException("No existe un curso con id ${source.courseId}") }
        val creator = userRepository.findById(creatorId)
            .orElseThrow { NotFoundException("No existe un usuario con id $creatorId") }
        val assessment = Assessment(
            title = uniqueTitle(source.noteTitle),
            description = "Generada con IA a partir de la nota «${source.noteTitle.trim()}»",
            durationMinutes = questions.size * MINUTES_PER_QUESTION,
            type = AssessmentType.QUIZ,
            difficulty = difficulty,
            course = course,
            createdBy = creator,
            questions = questions,
        )
        return assessmentMapper.toResponse(assessmentRepository.saveAndFlush(assessment))
    }

    private fun uniqueTitle(noteTitle: String): String {
        val base = "Quiz IA - ${noteTitle.trim()}".take(TITLE_BASE_MAX_LENGTH)
        if (!assessmentRepository.existsByTitle(base)) return base
        var suffix = 2
        while (assessmentRepository.existsByTitle("$base ($suffix)")) suffix++
        return "$base ($suffix)"
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

    private data class GenerationSource(val courseId: Long, val noteTitle: String, val content: String)

    private companion object {
        const val MINUTES_PER_QUESTION = 2
        const val TITLE_BASE_MAX_LENGTH = 190
    }
}
