package ApunteIA.BackEnd.service.impl

import ApunteIA.BackEnd.dto.note.NoteResponse
import ApunteIA.BackEnd.entity.Note
import ApunteIA.BackEnd.entity.ProcessingStatus
import ApunteIA.BackEnd.exception.ConflictException
import ApunteIA.BackEnd.exception.NotFoundException
import ApunteIA.BackEnd.mapper.NoteMapper
import ApunteIA.BackEnd.repository.NoteRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/** Persiste las transiciones por separado de la llamada HTTP al proveedor. */
@Service
class NoteProcessingStateService(
    private val noteRepository: NoteRepository,
    private val noteMapper: NoteMapper,
) {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun begin(id: Long): String {
        val note = findNoteOrThrow(id)
        if (note.processingStatus != ProcessingStatus.PENDING) {
            throw ConflictException("Solo se pueden procesar notas en estado PENDING; la nota está en ${note.processingStatus}")
        }
        note.processingStatus = ProcessingStatus.PROCESSING
        return noteRepository.saveAndFlush(note).content
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun complete(id: Long, summary: String): NoteResponse {
        val note = findNoteOrThrow(id)
        requireProcessing(note)
        note.summary = summary
        note.processingStatus = ProcessingStatus.COMPLETED
        return noteMapper.toResponse(noteRepository.saveAndFlush(note))
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun fail(id: Long) {
        val note = findNoteOrThrow(id)
        requireProcessing(note)
        note.processingStatus = ProcessingStatus.FAILED
        noteRepository.saveAndFlush(note)
    }

    private fun requireProcessing(note: Note) {
        if (note.processingStatus != ProcessingStatus.PROCESSING) {
            throw ConflictException("La nota debe estar en estado PROCESSING para finalizar su procesamiento")
        }
    }

    private fun findNoteOrThrow(id: Long): Note = noteRepository.findById(id)
        .orElseThrow { NotFoundException("No existe una nota con id $id") }
}
