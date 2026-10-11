package ApunteIA.BackEnd.dto.user

import ApunteIA.BackEnd.entity.AuthProvider
import ApunteIA.BackEnd.entity.UserStatus
import jakarta.validation.constraints.NotNull
import java.time.LocalDateTime

/** Salida de los endpoints de usuario. NUNCA incluye password_hash ni provider_user_id. */
data class UserResponse(
    val id: Long,
    val email: String,
    val fullName: String,
    val role: String,
    val privilege: String,
    val authProvider: AuthProvider,
    val status: UserStatus,
    val lastLoginAt: LocalDateTime?,
    val createdAt: LocalDateTime?,
    val updatedAt: LocalDateTime?,
)

/** Entrada de PATCH /users/{id}/status. Ejemplo: { "status": "BLOCKED" } */
data class UpdateUserStatusRequest(
    @field:NotNull(message = "El estado es obligatorio (ACTIVE, BLOCKED o DELETED)")
    val status: UserStatus? = null,
)