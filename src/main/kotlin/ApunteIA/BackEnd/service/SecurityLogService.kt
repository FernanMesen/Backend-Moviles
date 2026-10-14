package ApunteIA.BackEnd.service

import ApunteIA.BackEnd.dto.securitylog.SecurityLogResponse

interface SecurityLogService {
    fun findAll(
        userId: Long? = null,
        eventType: String? = null,
        anonymousOnly: Boolean = false,
    ): List<SecurityLogResponse>
    fun findById(id: Long): SecurityLogResponse
}
