package ApunteIA.BackEnd.service

import ApunteIA.BackEnd.dto.note.NoteResponse
import ApunteIA.BackEnd.dto.note.CreateNoteRequest
import ApunteIA.BackEnd.dto.note.UpdateNoteRequest
import ApunteIA.BackEnd.entity.ProcessingStatus

interface NoteService {
    fun findAll(userId: Long? = null, courseId: Long? = null, status: ProcessingStatus? = null): List<NoteResponse>
    fun findById(id: Long): NoteResponse
    fun create(request: CreateNoteRequest): NoteResponse
    fun update(id: Long, request: UpdateNoteRequest): NoteResponse
    fun processWithAi(id: Long, language: String? = null): NoteResponse
    fun updateStatus(id: Long, status: ProcessingStatus): NoteResponse
    fun delete(id: Long)
}
