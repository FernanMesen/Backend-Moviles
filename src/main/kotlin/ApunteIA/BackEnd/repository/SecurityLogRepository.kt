package ApunteIA.BackEnd.repository

import ApunteIA.BackEnd.entity.SecurityLog
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface SecurityLogRepository : JpaRepository<SecurityLog, Long> {
    fun findAllByUser_IdOrderByOccurredAtDesc(userId: Long): List<SecurityLog>
    fun findAllByEventTypeOrderByOccurredAtDesc(eventType: String): List<SecurityLog>
    fun findAllByUserIsNullOrderByOccurredAtDesc(): List<SecurityLog>
}
