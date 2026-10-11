package ApunteIA.BackEnd.service.impl

import ApunteIA.BackEnd.dto.note.NoteResponse
import ApunteIA.BackEnd.dto.note.SaveNoteRequest
import ApunteIA.BackEnd.entity.Note
import ApunteIA.BackEnd.entity.ProcessingStatus
import ApunteIA.BackEnd.exception.ConflictException
import ApunteIA.BackEnd.exception.NotFoundException
import ApunteIA.BackEnd.mapper.NoteMapper
import ApunteIA.BackEnd.repository.CourseRepository
import ApunteIA.BackEnd.repository.NoteRepository
import ApunteIA.BackEnd.repository.UserRepository
import ApunteIA.BackEnd.service.NoteService
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class NoteServiceImpl(
    private val noteRepository: NoteRepository,
    private val userRepository: UserRepository,
    private val courseRepository: CourseRepository,
    private val noteMapper: NoteMapper,
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
    override fun create(request: SaveNoteRequest): NoteResponse {
        val (user, course) = findRelations(request)
        val note = noteMapper.toEntity(request, user, course)
        return noteMapper.toResponse(noteRepository.saveAndFlush(note))
    }

    @Transactional
    override fun update(id: Long, request: SaveNoteRequest): NoteResponse {
        val note = findNoteOrThrow(id)
        val (user, course) = findRelations(request)
        note.title = request.title.trim()
        note.content = request.content.trim()
        note.user = user
        note.course = course
        return noteMapper.toResponse(noteRepository.saveAndFlush(note))
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

    private fun findRelations(request: SaveNoteRequest) =
        userRepository.findById(requireNotNull(request.userId))
            .orElseThrow { NotFoundException("No existe un usuario con id ${request.userId}") } to
            courseRepository.findById(requireNotNull(request.courseId))
                .orElseThrow { NotFoundException("No existe un curso con id ${request.courseId}") }
}
