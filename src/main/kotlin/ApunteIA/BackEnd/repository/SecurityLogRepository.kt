package ApunteIA.BackEnd.repository

import ApunteIA.BackEnd.entity.SecurityLog
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

@Repository
interface SecurityLogRepository : JpaRepository<SecurityLog, Long> {
    fun findAllByUser_IdOrderByOccurredAtDesc(userId: Long): List<SecurityLog>
    fun findAllByEventTypeOrderByOccurredAtDesc(eventType: String): List<SecurityLog>
    fun findAllByUserIsNullOrderByOccurredAtDesc(): List<SecurityLog>

    @Modifying
    @Query("update SecurityLog s set s.user = null, s.details = null, s.ipAddress = null where s.user.id = :userId")
    fun anonymizeUser(@Param("userId") userId: Long): Int
}
