package ApunteIA.BackEnd.mapper

import ApunteIA.BackEnd.dto.note.NoteResponse
import ApunteIA.BackEnd.dto.note.CreateNoteRequest
import ApunteIA.BackEnd.entity.Course
import ApunteIA.BackEnd.entity.Note
import ApunteIA.BackEnd.entity.User
import org.springframework.stereotype.Component

/** Convierte notas mientras sus relaciones LAZY están accesibles dentro del servicio transaccional. */
@Component
class NoteMapper {
    fun toResponse(note: Note) = NoteResponse(
        id = requireNotNull(note.id) { "La nota no ha sido guardada" },
        title = note.title,
        content = note.content,
        summary = note.summary,
        userId = requireNotNull(note.user.id) { "El usuario de la nota no tiene id" },
        courseId = requireNotNull(note.course.id) { "El curso de la nota no tiene id" },
        courseCode = note.course.code,
        processingStatus = note.processingStatus,
        createdAt = note.createdAt,
        updatedAt = note.updatedAt,
    )

    fun toEntity(request: CreateNoteRequest, user: User, course: Course) = Note(
        title = request.title.trim(),
        content = request.content.trim(),
        user = user,
        course = course,
    )
}
