package ApunteIA.BackEnd.service

import ApunteIA.BackEnd.dto.auth.LoginRequest
import ApunteIA.BackEnd.dto.auth.RegisterUserRequest
import ApunteIA.BackEnd.entity.*
import ApunteIA.BackEnd.exception.ForbiddenException
import ApunteIA.BackEnd.exception.UnauthorizedException
import ApunteIA.BackEnd.repository.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID
import kotlin.test.*

@SpringBootTest(properties = [
    "spring.datasource.url=jdbc:h2:mem:security-recording;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
    "spring.jpa.hibernate.ddl-auto=create-drop",
])
class SecurityLogRecordingTest @Autowired constructor(
    private val logs: SecurityLogService,
    private val auth: AuthService,
    private val users: UserService,
    private val userRepository: UserRepository,
    private val roleRepository: RoleRepository,
    private val privilegeRepository: PrivilegeRepository,
    private val logRepository: SecurityLogRepository,
    private val encoder: PasswordEncoder,
    private val transactionManager: PlatformTransactionManager,
) {
    private fun email() = "${UUID.randomUUID()}@test.com"

    // Repository transactions commit fixtures before REQUIRES_NEW tries to read them.
    private fun user(status: UserStatus = UserStatus.ACTIVE, google: Boolean = false): User {
        val role = roleRepository.findByName(Role.STUDENT)!!
        val privilege = privilegeRepository.findByName(Privilege.BASIC)!!
        return userRepository.saveAndFlush(User(
            email(), "Prueba", role, privilege,
            passwordHash = if (google) null else encoder.encode("123456"),
            authProvider = if (google) AuthProvider.GOOGLE else AuthProvider.LOCAL,
            status = status,
        ))
    }

    @Test
    fun `record associates committed user and truncates details`() {
        val user = user()
        logs.record("LOGIN", user.id, "x".repeat(1001), "127.0.0.1")
        val saved = logs.findAll(userId = user.id).single()
        assertEquals("LOGIN", saved.eventType)
        assertEquals(user.id, saved.userId)
        assertEquals("x".repeat(1000), saved.details)
        assertEquals("127.0.0.1", saved.ipAddress)
    }

    @Test
    fun `record permits anonymous events`() {
        val details = UUID.randomUUID().toString()
        logs.record("LOGIN_FAILED", details = details)
        val saved = logs.findAll(anonymousOnly = true).single { it.details == details }
        assertNull(saved.userId)
        assertNull(saved.ipAddress)
    }

    @Test
    fun `record retains event when user does not exist`() {
        val details = UUID.randomUUID().toString()
        logs.record("LOGIN_FAILED", Long.MAX_VALUE, details)
        assertNull(logs.findAll().single { it.details == details }.userId)
    }

    @Test
    fun `record rejects blank event type`() {
        assertThrows<IllegalArgumentException> { logs.record("   ") }
    }

    @Test
    fun `wrong password log survives actual outer transaction rollback`() {
        val user = user()
        val marker = UUID.randomUUID().toString()
        assertThrows<UnauthorizedException> {
            TransactionTemplate(transactionManager).executeWithoutResult {
                logRepository.saveAndFlush(SecurityLog("ROLLBACK_MARKER", details = marker))
                auth.login(LoginRequest(user.email, "incorrecta"))
            }
        }
        // Read after the outer transaction ended: its write vanished, the audit write survived.
        assertTrue(logs.findAll().none { it.details == marker })
        val saved = logs.findAll(userId = user.id).single()
        assertEquals("LOGIN_FAILED", saved.eventType)
        assertEquals(user.id, saved.userId)
        assertNull(userRepository.findById(user.id!!).orElseThrow().lastLoginAt)
    }

    @Test
    fun `successful login records LOGIN after validation`() {
        val user = user()
        auth.login(LoginRequest(user.email, "123456"))
        val saved = logs.findAll(userId = user.id).single()
        assertEquals("LOGIN", saved.eventType)
        assertEquals(user.id, saved.userId)
        assertNotNull(userRepository.findById(user.id!!).orElseThrow().lastLoginAt)
    }

    @Test
    fun `unknown email records anonymous failure`() {
        val email = email()
        assertThrows<UnauthorizedException> { auth.login(LoginRequest(email, "123456")) }
        val saved = logs.findAll(anonymousOnly = true).single { it.details?.contains(email) == true }
        assertEquals("LOGIN_FAILED", saved.eventType)
        assertNull(saved.userId)
        assertTrue(saved.details!!.contains("no registrado"))
    }

    @Test
    fun `deleted user records only failure`() {
        val user = user(UserStatus.DELETED)
        assertThrows<UnauthorizedException> { auth.login(LoginRequest(user.email, "123456")) }
        assertEquals("LOGIN_FAILED", logs.findAll(userId = user.id).single().eventType)
    }

    @Test
    fun `google account records only failure`() {
        val user = user(google = true)
        assertThrows<UnauthorizedException> { auth.login(LoginRequest(user.email, "123456")) }
        assertEquals("LOGIN_FAILED", logs.findAll(userId = user.id).single().eventType)
    }

    @Test
    fun `blocked user records failure despite forbidden exception`() {
        val user = user(UserStatus.BLOCKED)
        assertThrows<ForbiddenException> { auth.login(LoginRequest(user.email, "123456")) }
        assertEquals("LOGIN_FAILED", logs.findAll(userId = user.id).single().eventType)
    }

    @Test
    fun `registration associates USER_REGISTERED with created user`() {
        val email = email()
        val registered = auth.register(RegisterUserRequest("Nuevo", email, "123456"))
        assertTrue(userRepository.existsById(registered.id))
        val saved = logs.findAll().single { it.details?.contains(email) == true }
        assertEquals("USER_REGISTERED", saved.eventType)
        assertEquals(registered.id, saved.userId)
    }

    @Test
    fun `registration rollback removes both user and success event`() {
        val email = email()
        TransactionTemplate(transactionManager).executeWithoutResult { transaction ->
            val registered = auth.register(RegisterUserRequest("Nuevo", email, "123456"))
            assertEquals(registered.id, logs.findAll(userId = registered.id).single().userId)
            transaction.setRollbackOnly()
        }
        assertFalse(userRepository.existsByEmail(email))
        assertTrue(logs.findAll().none { it.details?.contains(email) == true })
    }

    @Test
    fun `status change records previous and new status`() {
        val user = user()
        users.updateStatus(user.id!!, UserStatus.BLOCKED)
        val saved = logs.findAll(userId = user.id).single()
        assertEquals("USER_STATUS_CHANGED", saved.eventType)
        assertEquals(user.id, saved.userId)
        assertEquals("Estado cambiado de ACTIVE a BLOCKED", saved.details)
        assertEquals(UserStatus.BLOCKED, userRepository.findById(user.id!!).orElseThrow().status)
    }

    @Test
    fun `unchanged status does not record a change`() {
        val user = user()
        users.updateStatus(user.id!!, UserStatus.ACTIVE)
        assertTrue(logs.findAll(userId = user.id).isEmpty())
    }
}
