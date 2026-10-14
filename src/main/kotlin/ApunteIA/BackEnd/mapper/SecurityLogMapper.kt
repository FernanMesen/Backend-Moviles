package ApunteIA.BackEnd.mapper

import ApunteIA.BackEnd.dto.securitylog.SecurityLogResponse
import ApunteIA.BackEnd.entity.SecurityLog
import org.springframework.stereotype.Component

/** Se utiliza dentro de un servicio @Transactional para acceder al usuario LAZY, si existe. */
@Component
class SecurityLogMapper {
    fun toResponse(log: SecurityLog): SecurityLogResponse {
        val user = log.user
        return SecurityLogResponse(
            id = requireNotNull(log.id) { "El log de seguridad no ha sido guardado" },
            eventType = log.eventType,
            userId = user?.let { requireNotNull(it.id) { "El usuario del log de seguridad no tiene id" } },
            userFullName = user?.fullName,
            details = log.details,
            ipAddress = log.ipAddress,
            occurredAt = log.occurredAt,
        )
    }
}
