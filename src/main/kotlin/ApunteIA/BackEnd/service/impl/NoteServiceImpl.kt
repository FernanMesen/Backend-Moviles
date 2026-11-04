package ApunteIA.BackEnd.service.impl

import ApunteIA.BackEnd.ai.AiClient
import ApunteIA.BackEnd.ai.AiLanguage
import ApunteIA.BackEnd.dto.note.NoteResponse
import ApunteIA.BackEnd.dto.note.CreateNoteRequest
import ApunteIA.BackEnd.dto.note.UpdateNoteRequest
import ApunteIA.BackEnd.entity.Note
import ApunteIA.BackEnd.entity.ProcessingStatus
import ApunteIA.BackEnd.exception.ConflictException
import ApunteIA.BackEnd.exception.AiProviderException
import ApunteIA.BackEnd.exception.AiUnavailableException
import ApunteIA.BackEnd.exception.NotFoundException
import ApunteIA.BackEnd.mapper.NoteMapper
import ApunteIA.BackEnd.repository.CourseRepository
import ApunteIA.BackEnd.repository.NoteRepository
import ApunteIA.BackEnd.repository.UserRepository
import ApunteIA.BackEnd.service.NoteService
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class NoteServiceImpl(
    private val noteRepository: NoteRepository,
    private val userRepository: UserRepository,
    private val courseRepository: CourseRepository,
    private val noteMapper: NoteMapper,
    private val aiClient: AiClient,
    private val noteProcessingStateService: NoteProcessingStateService,
) : NoteService {

    override fun findAll(userId: Long?, courseId: Long?, status: ProcessingStatus?): List<NoteResponse> {
        val matchingUserAndCourse = when {
            userId != null && courseId != null -> noteRepository.findAllByUser_IdAndCourse_Id(userId, courseId)
            userId != null -> noteRepository.findAllByUser_Id(userId)
            courseId != null -> noteRepository.findAllByCourse_Id(courseId)
            else -> noteRepository.findAll(Sort.by(Sort.Direction.ASC, "id"))
        }
        val notes = if (status == null) matchingUserAndCourse
        else matchingUserAndCourse.filter { it.processingStatus == status }
        return notes.map(noteMapper::toResponse)
    }

    override fun findById(id: Long): NoteResponse = noteMapper.toResponse(findNoteOrThrow(id))

    @Transactional
    override fun create(request: CreateNoteRequest): NoteResponse {
        val (user, course) = findRelations(request.userId, request.courseId)
        val note = noteMapper.toEntity(request, user, course)
        return noteMapper.toResponse(noteRepository.saveAndFlush(note))
    }

    @Transactional
    override fun update(id: Long, request: UpdateNoteRequest): NoteResponse {
        val note = findNoteOrThrow(id)
        val (user, course) = findRelations(request.userId, request.courseId)
        note.title = request.title.trim()
        note.content = request.content.trim()
        request.summary?.let { note.summary = it.trim().ifBlank { null } }
        request.noteDate?.let { note.noteDate = it }
        note.user = user
        note.course = course
        return noteMapper.toResponse(noteRepository.saveAndFlush(note))
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    override fun processWithAi(id: Long, language: String?): NoteResponse {
        val resolvedLanguage = AiLanguage.resolve(language)
        val content = noteProcessingStateService.begin(id)
        return try {
            val summary = aiClient.complete(
                systemPrompt(resolvedLanguage),
                "Título: ${content.title}\n\n${content.content}",
            )
            noteProcessingStateService.complete(id, summary)
        } catch (ex: AiProviderException) {
            noteProcessingStateService.fail(id)
            throw ex
        } catch (ex: AiUnavailableException) {
            noteProcessingStateService.fail(id)
            throw ex
        }
    }

    @Transactional
    override fun updateStatus(id: Long, status: ProcessingStatus): NoteResponse {
        val note = findNoteOrThrow(id)
        val validNext = when (note.processingStatus) {
            ProcessingStatus.PENDING -> setOf(ProcessingStatus.PROCESSING)
            ProcessingStatus.PROCESSING -> setOf(ProcessingStatus.COMPLETED, ProcessingStatus.FAILED)
            ProcessingStatus.COMPLETED, ProcessingStatus.FAILED -> emptySet()
        }
        if (status !in validNext) {
            throw ConflictException("No se permite cambiar el estado de ${note.processingStatus} a $status")
        }
        note.processingStatus = status
        return noteMapper.toResponse(noteRepository.saveAndFlush(note))
    }

    @Transactional
    override fun delete(id: Long) {
        noteRepository.delete(findNoteOrThrow(id))
        noteRepository.flush()
    }

    private fun findNoteOrThrow(id: Long): Note = noteRepository.findById(id)
        .orElseThrow { NotFoundException("No existe una nota con id $id") }

    private fun findRelations(userId: Long?, courseId: Long?) =
        userRepository.findById(requireNotNull(userId))
            .orElseThrow { NotFoundException("No existe un usuario con id $userId") } to
            courseRepository.findById(requireNotNull(courseId))
                .orElseThrow { NotFoundException("No existe un curso con id $courseId") }

    internal companion object {
        const val SYSTEM_PROMPT = """You are a study assistant. Summarize the source notes in at most 8 clear bullet points,
covering key concepts while preserving meaning. Do not add facts that are absent from the notes."""

        fun systemPrompt(language: AiLanguage): String = """${language.instruction}

$SYSTEM_PROMPT

FINAL OUTPUT CONSTRAINT: ${language.instruction}"""
    }
}

