package ApunteIA.BackEnd.service.impl

import ApunteIA.BackEnd.dto.auth.LoginRequest
import ApunteIA.BackEnd.dto.auth.LoginResponse
import ApunteIA.BackEnd.dto.auth.RegisterUserRequest
import ApunteIA.BackEnd.dto.auth.ChangePasswordRequest
import ApunteIA.BackEnd.dto.auth.DirectResetPasswordRequest
import ApunteIA.BackEnd.dto.auth.ForgotPasswordRequest
import ApunteIA.BackEnd.dto.auth.ResetPasswordRequest
import ApunteIA.BackEnd.dto.user.UserResponse
import ApunteIA.BackEnd.entity.AuthProvider
import ApunteIA.BackEnd.entity.Privilege
import ApunteIA.BackEnd.entity.Role
import ApunteIA.BackEnd.entity.UserStatus
import ApunteIA.BackEnd.entity.PasswordResetToken
import ApunteIA.BackEnd.entity.DeletedAccountTombstone
import ApunteIA.BackEnd.exception.ConflictException
import ApunteIA.BackEnd.exception.EmailDeliveryException
import ApunteIA.BackEnd.exception.BadRequestException
import ApunteIA.BackEnd.exception.ForbiddenException
import ApunteIA.BackEnd.exception.UnauthorizedException
import ApunteIA.BackEnd.mapper.UserMapper
import ApunteIA.BackEnd.repository.PrivilegeRepository
import ApunteIA.BackEnd.repository.RoleRepository
import ApunteIA.BackEnd.repository.UserRepository
import ApunteIA.BackEnd.repository.PasswordResetTokenRepository
import ApunteIA.BackEnd.repository.DeletedAccountTombstoneRepository
import ApunteIA.BackEnd.security.JwtService
import ApunteIA.BackEnd.service.AuthService
import ApunteIA.BackEnd.service.SecurityLogService
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.env.Environment
import org.springframework.core.env.Profiles
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.util.UriComponentsBuilder
import java.time.LocalDateTime
import java.time.Instant
import java.security.MessageDigest
import java.util.UUID

/**
 * Reglas: email sin importar mayúsculas · BCrypt · mismo mensaje para email inexistente y clave mala ·
 * Google sin contraseña → 401 · DELETED → 401 · BLOCKED → 403 · login actualiza last_login_at y devuelve un JWT ·
 * registro rechaza duplicados (409) y crea STUDENT + BASIC.
 */
@Service
class AuthServiceImpl(
    private val userRepository: UserRepository,
    private val roleRepository: RoleRepository,
    private val privilegeRepository: PrivilegeRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtService: JwtService,
    private val userMapper: UserMapper,
    private val securityLogService: SecurityLogService,
    private val resetTokenRepository: PasswordResetTokenRepository,
    private val tombstoneRepository: DeletedAccountTombstoneRepository,
    private val brevoEmailService: BrevoEmailService,
    private val environment: Environment,
    @param:Value("\${app.password-reset.url}") private val resetUrl: String,
    @param:Value("\${app.auth.direct-reset.enabled:false}") private val directResetEnabled: Boolean,
) : AuthService {

    @Transactional
    override fun login(request: LoginRequest): LoginResponse {
        val user = userRepository.findByEmail(UserMapper.normalizeEmail(request.email))
            ?: run {
                securityLogService.recordIndependent(SecurityLogService.LOGIN_FAILED,
                    details = "Email no registrado: ${UserMapper.normalizeEmail(request.email)}")
                throw UnauthorizedException(ACCOUNT_NOT_FOUND)
            }

        if (user.status == UserStatus.DELETED) {
            securityLogService.recordIndependent(SecurityLogService.LOGIN_FAILED, user.id, "Usuario eliminado")
            throw UnauthorizedException(INVALID_CREDENTIALS)
        }

        val passwordHash = user.passwordHash
        if (user.authProvider == AuthProvider.GOOGLE || passwordHash == null) {
            securityLogService.recordIndependent(SecurityLogService.LOGIN_FAILED, user.id, "Cuenta Google o sin contraseña local")
            throw UnauthorizedException("Esta cuenta inicia sesión con Google, no con contraseña")
        }

        if (!passwordEncoder.matches(request.password, passwordHash)) {
            securityLogService.recordIndependent(SecurityLogService.LOGIN_FAILED, user.id, "Contraseña incorrecta")
            throw UnauthorizedException(INVALID_CREDENTIALS)
        }

        // Se revisa después de la contraseña para no confirmar el estado a quien no la conoce
        if (user.status == UserStatus.BLOCKED) {
            securityLogService.recordIndependent(SecurityLogService.LOGIN_FAILED, user.id, "Usuario bloqueado")
            throw ForbiddenException("La cuenta está bloqueada. Contacte al administrador")
        }

        user.lastLoginAt = LocalDateTime.now() // la transacción hace el UPDATE al terminar
        val response = userMapper.toLoginResponse(user, jwtService.generate(user), jwtService.expirationSeconds)
        securityLogService.record(SecurityLogService.LOGIN, user.id)
        return response
    }

    @Transactional
    override fun register(request: RegisterUserRequest): UserResponse {
        val email = UserMapper.normalizeEmail(request.email)
        if (tombstoneRepository.existsByEmailHash(emailHash(email))) {
            throw ConflictException("Esta cuenta fue eliminada permanentemente y no puede volver a registrarse")
        }
        if (userRepository.existsByEmail(email)) {
            throw ConflictException("Ya existe un usuario registrado con el email $email")
        }

        val role = roleRepository.findByName(Role.STUDENT)
            ?: roleRepository.save(Role(name = Role.STUDENT, description = "Estudiante"))
        val privilege = privilegeRepository.findByName(Privilege.BASIC)
            ?: privilegeRepository.save(Privilege(name = Privilege.BASIC, description = "Acceso básico"))

        val user = userMapper.toEntity(request, role, privilege, passwordEncoder.encode(request.password))
        val saved = userRepository.saveAndFlush(user)
        securityLogService.record(SecurityLogService.USER_REGISTERED, saved.id, "Usuario registrado: $email")
        return userMapper.toResponse(saved)
    }

    @Transactional
    override fun changePassword(userId: Long, request: ChangePasswordRequest) {
        val user = userRepository.findById(userId).orElseThrow {
            UnauthorizedException("La cuenta autenticada ya no existe")
        }
        val currentHash = user.passwordHash
        if (user.authProvider != AuthProvider.LOCAL || currentHash == null) {
            throw BadRequestException("Esta cuenta no tiene una contraseña local para cambiar")
        }
        if (!passwordEncoder.matches(request.currentPassword, currentHash)) {
            throw BadRequestException("La contraseña actual es incorrecta")
        }
        if (passwordEncoder.matches(request.newPassword, currentHash)) {
            throw BadRequestException("La nueva contraseña debe ser diferente de la actual")
        }
        user.passwordHash = passwordEncoder.encode(request.newPassword)
        securityLogService.record(SecurityLogService.PASSWORD_CHANGED, user.id)
    }

    @Transactional
    override fun requestPasswordReset(request: ForgotPasswordRequest) {
        val user = userRepository.findByEmail(UserMapper.normalizeEmail(request.email)) ?: return
        if (user.status != UserStatus.ACTIVE || user.authProvider != AuthProvider.LOCAL || user.passwordHash == null) return

        resetTokenRepository.deleteAllByUser_Id(requireNotNull(user.id))
        val token = "${UUID.randomUUID()}${UUID.randomUUID()}"
        resetTokenRepository.save(PasswordResetToken(
            user = user,
            tokenHash = sha256(token),
            expiresAt = Instant.now().plusSeconds(30 * 60),
        ))
        val resetLink = UriComponentsBuilder.fromUriString(resetUrl)
            .queryParam("token", token)
            .build()
            .encode()
            .toUriString()
        try {
            brevoEmailService.sendPasswordReset(user.email, resetLink)
        } catch (_: EmailDeliveryException) {
            // Keep the public response indistinguishable for unknown, ineligible, and failed delivery cases.
            resetTokenRepository.deleteAllByUser_Id(requireNotNull(user.id))
        }
    }

    @Transactional
    override fun resetPassword(request: ResetPasswordRequest) {
        val reset = resetTokenRepository.findByTokenHash(sha256(request.token))
            ?: throw BadRequestException("El enlace de recuperación no es válido o venció")
        if (reset.usedAt != null || reset.expiresAt.isBefore(Instant.now()) || reset.user.status != UserStatus.ACTIVE) {
            throw BadRequestException("El enlace de recuperación no es válido o venció")
        }
        reset.user.passwordHash = passwordEncoder.encode(request.newPassword)
        reset.usedAt = Instant.now()
        resetTokenRepository.deleteAllByUser_Id(requireNotNull(reset.user.id))
        securityLogService.record(SecurityLogService.PASSWORD_CHANGED, reset.user.id, "Contraseña restablecida por correo")
    }

    @Transactional
    override fun directResetPassword(request: DirectResetPasswordRequest) {
        val datasourceUrl = environment.getProperty("spring.datasource.url").orEmpty()
        val localOrTestProfile = environment.acceptsProfiles(Profiles.of("local", "test"))
        if (!directResetEnabled || !localOrTestProfile || !datasourceUrl.startsWith("jdbc:h2:") ||
            environment.getProperty("RENDER")?.equals("true", ignoreCase = true) == true
        ) {
            throw ForbiddenException("La recuperación directa solo está disponible en pruebas locales")
        }

        val email = UserMapper.normalizeEmail(request.email)
        if (email !in DIRECT_RESET_TEST_EMAILS) {
            throw BadRequestException("La recuperación directa solo admite cuentas ficticias de prueba")
        }
        val user = userRepository.findByEmail(email)
            ?: throw BadRequestException("No existe una cuenta ficticia de prueba con ese correo")
        if (user.status != UserStatus.ACTIVE || user.authProvider != AuthProvider.LOCAL || user.passwordHash == null) {
            throw BadRequestException("La cuenta no es elegible para la recuperación directa")
        }

        user.passwordHash = passwordEncoder.encode(request.newPassword)
        val savedUser = userRepository.saveAndFlush(user)
        val savedHash = savedUser.passwordHash
            ?: throw IllegalStateException("La contraseña no se guardó correctamente")
        if (!passwordEncoder.matches(request.newPassword, savedHash)) {
            throw IllegalStateException("La contraseña no se guardó correctamente")
        }
        securityLogService.record(SecurityLogService.PASSWORD_CHANGED, savedUser.id, "Recuperación directa de cuenta ficticia")
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    private fun emailHash(email: String): String = DeletedAccountTombstone.emailHash(email)

    private companion object {
        const val INVALID_CREDENTIALS = "Credenciales inválidas"
        const val ACCOUNT_NOT_FOUND = "No existe una cuenta registrada con este correo."
        val DIRECT_RESET_TEST_EMAILS = setOf(
            "demo@apunteia.com",
            "admin@apunteia.com",
            "profe@apunteia.com",
            "bloqueado@apunteia.com",
        )
    }
}
