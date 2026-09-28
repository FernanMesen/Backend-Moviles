package ApunteIA.BackEnd.repository

import ApunteIA.BackEnd.entity.AuthProvider
import ApunteIA.BackEnd.entity.Privilege
import ApunteIA.BackEnd.entity.Role
import ApunteIA.BackEnd.entity.User
import ApunteIA.BackEnd.entity.UserStatus
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.dao.DataIntegrityViolationException
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pruebas de persistencia de Role, Privilege y User sobre H2 en memoria.
 * Cada prueba corre en su propia transacción y se revierte al terminar.
 */
@DataJpaTest
class IdentityRepositoryTest @Autowired constructor(
    private val roleRepository: RoleRepository,
    private val privilegeRepository: PrivilegeRepository,
    private val userRepository: UserRepository,
    private val entityManager: EntityManager,
) {
    private lateinit var student: Role
    private lateinit var basic: Privilege

    @BeforeEach
    fun setUp() {
        student = roleRepository.save(Role(Role.STUDENT, "Estudiante"))
        basic = privilegeRepository.save(Privilege(Privilege.BASIC, "Acceso básico"))
    }

    private fun newUser(email: String, role: Role = student, privilege: Privilege = basic) =
        User(email = email, fullName = "Usuario Prueba", role = role, privilege = privilege, passwordHash = "hash")

    /** Vacía el contexto para que las lecturas vayan realmente a la base de datos. */
    private fun flushAndClear() {
        entityManager.flush()
        entityManager.clear()
    }

    // ---------- Role / Privilege ----------

    @Test
    fun `guarda y busca un rol por nombre`() {
        flushAndClear()
        val found = roleRepository.findByName(Role.STUDENT)
        assertNotNull(found)
        assertNotNull(found.id)
        assertEquals("Estudiante", found.description)
        assertTrue(roleRepository.existsByName(Role.STUDENT))
        assertNull(roleRepository.findByName("NO_EXISTE"))
    }

    @Test
    fun `guarda y busca un privilegio por nombre`() {
        flushAndClear()
        val found = privilegeRepository.findByName(Privilege.BASIC)
        assertNotNull(found)
        assertEquals("Acceso básico", found.description)
    }

    @Test
    fun `no permite dos roles con el mismo nombre`() {
        assertThrows<DataIntegrityViolationException> {
            roleRepository.saveAndFlush(Role(Role.STUDENT, "duplicado"))
        }
    }

    @Test
    fun `no permite dos privilegios con el mismo nombre`() {
        assertThrows<DataIntegrityViolationException> {
            privilegeRepository.saveAndFlush(Privilege(Privilege.BASIC, "duplicado"))
        }
    }

    // ---------- User ----------

    @Test
    fun `persiste un usuario con su rol y privilegio`() {
        val saved = userRepository.save(newUser("maya@apunteia.com"))
        flushAndClear()

        val found = userRepository.findByEmail("maya@apunteia.com")
        assertNotNull(found)
        assertEquals(saved.id, found.id)
        assertEquals(Role.STUDENT, found.role.name)          // FK role_id resuelta
        assertEquals(Privilege.BASIC, found.privilege.name)  // FK privilege_id resuelta
    }

    @Test
    fun `aplica valores por defecto y timestamps`() {
        userRepository.save(newUser("defaults@apunteia.com"))
        flushAndClear()

        val found = userRepository.findByEmail("defaults@apunteia.com")!!
        assertEquals(AuthProvider.LOCAL, found.authProvider)
        assertEquals(UserStatus.ACTIVE, found.status)
        assertNotNull(found.createdAt)
        assertNotNull(found.updatedAt)
        assertNull(found.lastLoginAt)
    }

    @Test
    fun `no permite dos usuarios con el mismo email`() {
        userRepository.saveAndFlush(newUser("repetido@apunteia.com"))
        assertThrows<DataIntegrityViolationException> {
            userRepository.saveAndFlush(newUser("repetido@apunteia.com"))
        }
    }

    @Test
    fun `no permite un usuario sin rol existente`() {
        val rolSinGuardar = Role("FANTASMA")   // nunca se persistió
        assertThrows<Exception> {
            userRepository.saveAndFlush(newUser("sinrol@apunteia.com", role = rolSinGuardar))
        }
    }

    @Test
    fun `busca usuario de Google por proveedor e id externo`() {
        userRepository.save(
            newUser("ana@gmail.com").apply {
                passwordHash = null
                authProvider = AuthProvider.GOOGLE
                providerUserId = "google-123"
            }
        )
        flushAndClear()

        val found = userRepository.findByAuthProviderAndProviderUserId(AuthProvider.GOOGLE, "google-123")
        assertNotNull(found)
        assertEquals("ana@gmail.com", found.email)
        assertNull(found.passwordHash)
        assertNull(userRepository.findByAuthProviderAndProviderUserId(AuthProvider.LOCAL, "google-123"))
    }

    @Test
    fun `filtra usuarios por estado, rol y privilegio`() {
        val admin = roleRepository.save(Role(Role.ADMIN))
        val full = privilegeRepository.save(Privilege(Privilege.FULL_ACCESS))
        userRepository.save(newUser("a@apunteia.com"))
        userRepository.save(newUser("b@apunteia.com").apply { status = UserStatus.BLOCKED })
        userRepository.save(newUser("admin@apunteia.com", role = admin, privilege = full))
        flushAndClear()

        assertEquals(setOf("b@apunteia.com"), userRepository.findAllByStatus(UserStatus.BLOCKED).map { it.email }.toSet())
        assertEquals(setOf("a@apunteia.com", "b@apunteia.com"), userRepository.findAllByRole_Name(Role.STUDENT).map { it.email }.toSet())
        assertEquals(listOf("admin@apunteia.com"), userRepository.findAllByPrivilege_Name(Privilege.FULL_ACCESS).map { it.email })
    }

    @Test
    fun `actualiza el estado y la fecha de ultimo login`() {
        val user = userRepository.saveAndFlush(newUser("login@apunteia.com"))

        user.status = UserStatus.BLOCKED
        user.lastLoginAt = java.time.LocalDateTime.now()
        userRepository.saveAndFlush(user)
        flushAndClear()

        val found = userRepository.findById(user.id!!).orElseThrow()
        assertEquals(UserStatus.BLOCKED, found.status)
        assertNotNull(found.lastLoginAt)
        assertNotNull(found.createdAt)
        assertFalse(found.updatedAt!!.isBefore(found.createdAt))
    }
}