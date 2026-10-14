package ApunteIA.BackEnd.service.impl

import ApunteIA.BackEnd.dto.securitylog.SecurityLogResponse
import ApunteIA.BackEnd.entity.SecurityLog
import ApunteIA.BackEnd.exception.NotFoundException
import ApunteIA.BackEnd.mapper.SecurityLogMapper
import ApunteIA.BackEnd.repository.SecurityLogRepository
import ApunteIA.BackEnd.service.SecurityLogService
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class SecurityLogServiceImpl(
    private val securityLogRepository: SecurityLogRepository,
    private val securityLogMapper: SecurityLogMapper,
) : SecurityLogService {

    override fun findAll(userId: Long?, eventType: String?, anonymousOnly: Boolean): List<SecurityLogResponse> {
        val normalizedEventType = eventType?.trim()
        val logs = when {
            anonymousOnly -> securityLogRepository.findAllByUserIsNullOrderByOccurredAtDesc()
            userId != null -> securityLogRepository.findAllByUser_IdOrderByOccurredAtDesc(userId)
            normalizedEventType != null ->
                securityLogRepository.findAllByEventTypeOrderByOccurredAtDesc(normalizedEventType)
            else -> securityLogRepository.findAll(Sort.by(Sort.Direction.DESC, "occurredAt", "id"))
        }
        return logs
            .filter { userId == null || it.user?.id == userId }
            .filter { normalizedEventType == null || it.eventType == normalizedEventType }
            .sortedWith(compareByDescending<SecurityLog> { it.occurredAt }.thenByDescending { it.id })
            .map(securityLogMapper::toResponse)
    }

    override fun findById(id: Long): SecurityLogResponse = securityLogMapper.toResponse(
        securityLogRepository.findById(id)
            .orElseThrow { NotFoundException("No existe un log de seguridad con id $id") }
    )
}
