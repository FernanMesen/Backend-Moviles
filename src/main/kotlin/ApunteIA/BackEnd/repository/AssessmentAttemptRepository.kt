package ApunteIA.BackEnd.repository

import ApunteIA.BackEnd.entity.AssessmentAttempt
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface AssessmentAttemptRepository : JpaRepository<AssessmentAttempt, Long> {
    fun findAllByUser_IdOrderByCompletedAtDesc(userId: Long): List<AssessmentAttempt>
    fun findAllByAssessment_IdAndUser_Id(assessmentId: Long, userId: Long): List<AssessmentAttempt>
    fun existsByUser_IdAndAssessment_IdAndCompletedAt(userId: Long, assessmentId: Long, completedAt: Instant): Boolean
}