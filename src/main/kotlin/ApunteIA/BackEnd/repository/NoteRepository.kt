package ApunteIA.BackEnd.repository

import ApunteIA.BackEnd.entity.Note
import ApunteIA.BackEnd.entity.ProcessingStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface NoteRepository : JpaRepository<Note, Long> {
    fun findAllByUser_Id(userId: Long): List<Note>
    fun findAllByCourse_Id(courseId: Long): List<Note>
    fun findAllByUser_IdAndCourse_Id(userId: Long, courseId: Long): List<Note>
    fun findAllByProcessingStatus(processingStatus: ProcessingStatus): List<Note>
    fun deleteAllByUser_Id(userId: Long)
}
