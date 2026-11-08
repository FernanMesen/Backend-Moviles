package ApunteIA.BackEnd.seed

import ApunteIA.BackEnd.entity.AuthProvider
import ApunteIA.BackEnd.entity.Privilege
import ApunteIA.BackEnd.entity.Role
import ApunteIA.BackEnd.entity.User
import ApunteIA.BackEnd.entity.UserStatus
import ApunteIA.BackEnd.repository.PrivilegeRepository
import ApunteIA.BackEnd.repository.RoleRepository
import ApunteIA.BackEnd.repository.UserRepository
import ApunteIA.BackEnd.repository.DeletedAccountTombstoneRepository
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Datos de prueba de Identidad y Acceso (Persona A).
 * Es idempotente: si un rol, privilegio o email ya existe, no lo duplica.
 *
 * ORDEN DE CARGA DEL EQUIPO: este seeder va primero (@Order(1) en DataLoader),
 * porque Course, Enrollment, Note, Assessment, etc. necesitan usuarios existentes.
 */
@Component
class IdentitySeeder(
    private val roleRepository: RoleRepository,
    private val privilegeRepository: PrivilegeRepository,
    private val userRepository: UserRepository,
    private val tombstoneRepository: DeletedAccountTombstoneRepository,
    private val passwordEncoder: PasswordEncoder,
) {

    @Transactional
    fun seed() {
        val student = role(Role.STUDENT, "Estudiante que sube apuntes y hace quizzes")
        val admin = role(Role.ADMIN, "Administrador de la plataforma")

        val basic = privilege(Privilege.BASIC, "Acceso a sus propios apuntes, cursos y evaluaciones")
        val manager = privilege(Privilege.MANAGER, "Puede crear y administrar cursos")
        val fullAccess = privilege(Privilege.FULL_ACCESS, "Acceso total, incluida la gestión de usuarios")

        // Mismo usuario demo que usa el MockApiInterceptor del FrontEnd
        localUser("demo@apunteia.com", "Maya Rodríguez", "123456", student, basic)
        localUser("admin@apunteia.com", "Administrador ApunteIA", "Admin123!", admin, fullAccess)
        localUser("profe@apunteia.com", "Carlos Jiménez", "Profe123!", student, manager)
        localUser("bloqueado@apunteia.com", "Usuario Bloqueado", "123456", student, basic, UserStatus.BLOCKED)

        if (!userRepository.existsByEmail("google.user@gmail.com") && !wasPermanentlyDeleted("google.user@gmail.com")) {
            userRepository.save(
                User(
                    email = "google.user@gmail.com",
                    fullName = "Ana Google",
                    role = student,
                    privilege = basic,
                    passwordHash = null,
                    authProvider = AuthProvider.GOOGLE,
                    providerUserId = "google-sub-1234567890",
                )
            )
        }
    }

    private fun role(name: String, description: String): Role =
        roleRepository.findByName(name) ?: roleRepository.save(Role(name = name, description = description))

    private fun privilege(name: String, description: String): Privilege =
        privilegeRepository.findByName(name)
            ?: privilegeRepository.save(Privilege(name = name, description = description))

    private fun localUser(
        email: String,
        fullName: String,
        rawPassword: String,
        role: Role,
        privilege: Privilege,
        status: UserStatus = UserStatus.ACTIVE,
    ) {
        if (userRepository.existsByEmail(email) || wasPermanentlyDeleted(email)) return
        userRepository.save(
            User(
                email = email,
                fullName = fullName,
                role = role,
                privilege = privilege,
                passwordHash = passwordEncoder.encode(rawPassword),
                authProvider = AuthProvider.LOCAL,
                status = status,
            )
        )
    }

    private fun wasPermanentlyDeleted(email: String): Boolean {
        val emailHash = java.security.MessageDigest.getInstance("SHA-256")
            .digest(email.trim().lowercase().toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return tombstoneRepository.existsByEmailHash(emailHash)
    }
}
