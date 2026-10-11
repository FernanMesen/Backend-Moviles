package ApunteIA.BackEnd.mapper

import ApunteIA.BackEnd.dto.auth.LoginResponse
import ApunteIA.BackEnd.dto.auth.RegisterUserRequest
import ApunteIA.BackEnd.dto.user.UserResponse
import ApunteIA.BackEnd.entity.AuthProvider
import ApunteIA.BackEnd.entity.Privilege
import ApunteIA.BackEnd.entity.Role
import ApunteIA.BackEnd.entity.User
import org.springframework.stereotype.Component

/**
 * Convierte entre la entidad User y sus DTOs.
 * role y privilege son LAZY y open-in-view está apagado, así que se llama
 * DENTRO de un método @Transactional del servicio.
 */
@Component
class UserMapper {

    fun toResponse(user: User): UserResponse = UserResponse(
        id = requireNotNull(user.id) { "El usuario no ha sido guardado" },
        email = user.email,
        fullName = user.fullName,
        role = user.role.name,
        privilege = user.privilege.name,
        authProvider = user.authProvider,
        status = user.status,
        lastLoginAt = user.lastLoginAt,
        createdAt = user.createdAt,
        updatedAt = user.updatedAt,
    )

    fun toResponseList(users: List<User>): List<UserResponse> = users.map(this::toResponse)

    /** La contraseña ya llega hasheada desde el servicio: el mapper no conoce BCrypt. */
    fun toEntity(request: RegisterUserRequest, role: Role, privilege: Privilege, passwordHash: String): User = User(
        email = normalizeEmail(request.email),
        fullName = request.fullName.trim(),
        role = role,
        privilege = privilege,
        passwordHash = passwordHash,
        authProvider = AuthProvider.LOCAL,
    )

    fun toLoginResponse(user: User, token: String): LoginResponse = LoginResponse(
        token = token,
        userName = user.fullName,
        userId = requireNotNull(user.id),
        email = user.email,
        role = user.role.name,
    )

    companion object {
        fun normalizeEmail(email: String) = email.trim().lowercase()
    }
}