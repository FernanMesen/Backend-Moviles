package ApunteIA.BackEnd.dto.securitylog

import java.time.LocalDateTime

/** Los eventos anónimos no incluyen id ni nombre de usuario. */
data class SecurityLogResponse(
    val id: Long,
    val eventType: String,
    val userId: Long?,
    val userFullName: String?,
    val details: String?,
    val ipAddress: String?,
    val occurredAt: LocalDateTime?,
)
