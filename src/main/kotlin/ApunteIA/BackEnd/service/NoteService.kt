package ApunteIA.BackEnd.service

import ApunteIA.BackEnd.dto.note.NoteResponse
import ApunteIA.BackEnd.dto.note.SaveNoteRequest
import ApunteIA.BackEnd.entity.ProcessingStatus

interface NoteService {
    fun findAll(userId: Long? = null, courseId: Long? = null, status: ProcessingStatus? = null): List<NoteResponse>
    fun findById(id: Long): NoteResponse
    fun create(request: SaveNoteRequest): NoteResponse
    fun update(id: Long, request: SaveNoteRequest): NoteResponse
    fun updateStatus(id: Long, status: ProcessingStatus): NoteResponse
    fun delete(id: Long)
}
