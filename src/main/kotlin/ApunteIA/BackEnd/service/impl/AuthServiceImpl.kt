package ApunteIA.BackEnd.service.impl

import ApunteIA.BackEnd.dto.auth.LoginRequest
import ApunteIA.BackEnd.dto.auth.LoginResponse
import ApunteIA.BackEnd.dto.auth.RegisterUserRequest
import ApunteIA.BackEnd.dto.user.UserResponse
import ApunteIA.BackEnd.entity.AuthProvider
import ApunteIA.BackEnd.entity.Privilege
import ApunteIA.BackEnd.entity.Role
import ApunteIA.BackEnd.entity.UserStatus
import ApunteIA.BackEnd.exception.ConflictException
import ApunteIA.BackEnd.exception.ForbiddenException
import ApunteIA.BackEnd.exception.UnauthorizedException
import ApunteIA.BackEnd.mapper.UserMapper
import ApunteIA.BackEnd.repository.PrivilegeRepository
import ApunteIA.BackEnd.repository.RoleRepository
import ApunteIA.BackEnd.repository.UserRepository
import ApunteIA.BackEnd.service.AuthService
import ApunteIA.BackEnd.service.SecurityLogService
import ApunteIA.BackEnd.service.TokenGenerator
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * Reglas: email sin importar mayúsculas · BCrypt · mismo mensaje para email inexistente y clave mala ·
 * Google sin contraseña → 401 · DELETED → 401 · BLOCKED → 403 · login actualiza last_login_at ·
 * registro rechaza duplicados (409) y crea STUDENT + BASIC.
 */
@Service
class AuthServiceImpl(
    private val userRepository: UserRepository,
    private val roleRepository: RoleRepository,
    private val privilegeRepository: PrivilegeRepository,
    private val passwordEncoder: PasswordEncoder,
    private val tokenGenerator: TokenGenerator,
    private val userMapper: UserMapper,
    private val securityLogService: SecurityLogService,
) : AuthService {

    @Transactional
    override fun login(request: LoginRequest): LoginResponse {
        val user = userRepository.findByEmail(UserMapper.normalizeEmail(request.email))
            ?: run {
                securityLogService.recordIndependent(SecurityLogService.LOGIN_FAILED,
                    details = "Email no registrado: ${UserMapper.normalizeEmail(request.email)}")
                throw UnauthorizedException(INVALID_CREDENTIALS)
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
        val response = userMapper.toLoginResponse(user, tokenGenerator.generate())
        securityLogService.record(SecurityLogService.LOGIN, user.id)
        return response
    }

    @Transactional
    override fun register(request: RegisterUserRequest): UserResponse {
        val email = UserMapper.normalizeEmail(request.email)
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

    private companion object {
        const val INVALID_CREDENTIALS = "Credenciales inválidas"
    }
}
