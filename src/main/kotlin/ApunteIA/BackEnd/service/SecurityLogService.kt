package ApunteIA.BackEnd.service

import ApunteIA.BackEnd.dto.securitylog.SecurityLogResponse

interface SecurityLogService {
    /** Participa en la transacción actual para confirmar el evento junto con la operación. */
    fun record(eventType: String, userId: Long? = null, details: String? = null, ipAddress: String? = null)

    /** Confirma el evento por separado, incluso si la operación que lo originó hace rollback. */
    fun recordIndependent(eventType: String, userId: Long? = null, details: String? = null, ipAddress: String? = null)

    companion object {
        const val LOGIN = "LOGIN"
        const val LOGIN_FAILED = "LOGIN_FAILED"
        const val USER_REGISTERED = "USER_REGISTERED"
        const val PASSWORD_CHANGED = "PASSWORD_CHANGED"
        const val USER_STATUS_CHANGED = "USER_STATUS_CHANGED"
    }

    fun findAll(
        userId: Long? = null,
        eventType: String? = null,
        anonymousOnly: Boolean = false,
    ): List<SecurityLogResponse>
    fun findById(id: Long): SecurityLogResponse
}
