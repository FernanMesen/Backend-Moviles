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
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Pruebas de inserción, búsqueda y actualización de Role, Privilege y User. */
@DataJpaTest
class IdentityCrudTest @Autowired constructor(
    private val roleRepository: RoleRepository,
    private val privilegeRepository: PrivilegeRepository,
    private val userRepository: UserRepository,
    private val entityManager: EntityManager,
) {
    private lateinit var student: Role
    private lateinit var admin: Role
    private lateinit var basic: Privilege
    private lateinit var fullAccess: Privilege

    @BeforeEach
    fun setUp() {
        student = roleRepository.save(Role(Role.STUDENT, "Estudiante"))
        admin = roleRepository.save(Role(Role.ADMIN, "Administrador"))
        basic = privilegeRepository.save(Privilege(Privilege.BASIC, "Acceso básico"))
        fullAccess = privilegeRepository.save(Privilege(Privilege.FULL_ACCESS, "Acceso total"))
    }

    private fun newUser(email: String, name: String = "Usuario Prueba") =
        User(email = email, fullName = name, role = student, privilege = basic, passwordHash = "hash")

    private fun flushAndClear() {
        entityManager.flush()
        entityManager.clear()
    }

    // ---------- INSERT ----------

    @Test
    fun `insertar un rol le asigna un id`() {
        val saved = roleRepository.save(Role("PROFESSOR", "Profesor"))
        assertNotNull(saved.id)
    }

    @Test
    fun `insertar un privilegio le asigna un id`() {
        val saved = privilegeRepository.save(Privilege(Privilege.MANAGER, "Gestión de cursos"))
        assertNotNull(saved.id)
    }

    @Test
    fun `insertar varios usuarios con saveAll`() {
        userRepository.saveAll(
            listOf(newUser("a@apunteia.com"), newUser("b@apunteia.com"), newUser("c@apunteia.com"))
        )
        flushAndClear()

        assertEquals(3, userRepository.count())
    }

    @Test
    fun `insertar un usuario guarda todos sus campos`() {
        val lastLogin = LocalDateTime.of(2026, 9, 1, 10, 30)
        val saved = userRepository.save(
            User(
                email = "completo@apunteia.com",
                fullName = "Usuario Completo",
                role = admin,
                privilege = fullAccess,
                passwordHash = "hash-123",
                status = UserStatus.BLOCKED,
                lastLoginAt = lastLogin,
            )
        )
        flushAndClear()

        val found = userRepository.findById(saved.id!!).orElseThrow()
        assertEquals("completo@apunteia.com", found.email)
        assertEquals("Usuario Completo", found.fullName)
        assertEquals("hash-123", found.passwordHash)
        assertEquals(AuthProvider.LOCAL, found.authProvider)
        assertEquals(UserStatus.BLOCKED, found.status)
        assertEquals(lastLogin, found.lastLoginAt)
        assertEquals(Role.ADMIN, found.role.name)
        assertEquals(Privilege.FULL_ACCESS, found.privilege.name)
    }

    // ---------- FIND ----------

    @Test
    fun `buscar por id un rol y un privilegio`() {
        flushAndClear()

        assertEquals(Role.STUDENT, roleRepository.findById(student.id!!).orElseThrow().name)
        assertEquals(Privilege.BASIC, privilegeRepository.findById(basic.id!!).orElseThrow().name)
    }

    @Test
    fun `buscar por un id inexistente devuelve vacio`() {
        assertTrue(userRepository.findById(999_999L).isEmpty)
        assertTrue(roleRepository.findById(999_999L).isEmpty)
        assertFalse(userRepository.existsById(999_999L))
    }

    @Test
    fun `buscar un email inexistente devuelve null`() {
        userRepository.save(newUser("existe@apunteia.com"))
        flushAndClear()

        assertNull(userRepository.findByEmail("noexiste@apunteia.com"))
        assertFalse(userRepository.existsByEmail("noexiste@apunteia.com"))
        assertTrue(userRepository.existsByEmail("existe@apunteia.com"))
    }

    @Test
    fun `listar todos los roles y privilegios`() {
        flushAndClear()

        assertEquals(setOf(Role.STUDENT, Role.ADMIN), roleRepository.findAll().map { it.name }.toSet())
        assertEquals(setOf(Privilege.BASIC, Privilege.FULL_ACCESS), privilegeRepository.findAll().map { it.name }.toSet())
        assertEquals(2, roleRepository.count())
        assertEquals(2, privilegeRepository.count())
    }

    @Test
    fun `filtros sin coincidencias devuelven lista vacia`() {
        userRepository.save(newUser("activo@apunteia.com"))
        flushAndClear()

        assertTrue(userRepository.findAllByStatus(UserStatus.DELETED).isEmpty())
        assertTrue(userRepository.findAllByRole_Name(Role.ADMIN).isEmpty())
        assertTrue(userRepository.findAllByPrivilege_Name(Privilege.FULL_ACCESS).isEmpty())
    }

    // ---------- UPDATE ----------

    @Test
    fun `actualizar nombre y email de un usuario`() {
        val user = userRepository.save(newUser("viejo@apunteia.com", "Nombre Viejo"))
        flushAndClear()

        val loaded = userRepository.findById(user.id!!).orElseThrow()
        loaded.fullName = "Nombre Nuevo"
        loaded.email = "nuevo@apunteia.com"
        userRepository.save(loaded)
        flushAndClear()

        assertNull(userRepository.findByEmail("viejo@apunteia.com"))
        val found = userRepository.findByEmail("nuevo@apunteia.com")
        assertNotNull(found)
        assertEquals("Nombre Nuevo", found.fullName)
    }

    @Test
    fun `cambiar el rol y el privilegio de un usuario`() {
        val user = userRepository.save(newUser("ascenso@apunteia.com"))
        flushAndClear()

        val loaded = userRepository.findById(user.id!!).orElseThrow()
        loaded.role = roleRepository.findByName(Role.ADMIN)!!
        loaded.privilege = privilegeRepository.findByName(Privilege.FULL_ACCESS)!!
        userRepository.save(loaded)
        flushAndClear()

        val found = userRepository.findById(user.id!!).orElseThrow()
        assertEquals(Role.ADMIN, found.role.name)
        assertEquals(Privilege.FULL_ACCESS, found.privilege.name)
        assertTrue(userRepository.findAllByRole_Name(Role.STUDENT).isEmpty())
    }

    @Test
    fun `cambiar un usuario a cuenta de Google`() {
        val user = userRepository.save(newUser("migrar@gmail.com"))
        flushAndClear()

        val loaded = userRepository.findById(user.id!!).orElseThrow()
        loaded.authProvider = AuthProvider.GOOGLE
        loaded.providerUserId = "google-abc"
        loaded.passwordHash = null
        userRepository.save(loaded)
        flushAndClear()

        val found = userRepository.findByAuthProviderAndProviderUserId(AuthProvider.GOOGLE, "google-abc")
        assertNotNull(found)
        assertEquals("migrar@gmail.com", found.email)
        assertNull(found.passwordHash)
    }

    @Test
    fun `actualizar la descripcion de un rol y de un privilegio`() {
        flushAndClear()

        val role = roleRepository.findByName(Role.STUDENT)!!
        role.description = "Estudiante universitario"
        roleRepository.save(role)
        val privilege = privilegeRepository.findByName(Privilege.BASIC)!!
        privilege.description = "Solo sus propios datos"
        privilegeRepository.save(privilege)
        flushAndClear()

        assertEquals("Estudiante universitario", roleRepository.findByName(Role.STUDENT)!!.description)
        assertEquals("Solo sus propios datos", privilegeRepository.findByName(Privilege.BASIC)!!.description)
    }

    @Test
    fun `no permite cambiar el email a uno que ya existe`() {
        userRepository.save(newUser("uno@apunteia.com"))
        val second = userRepository.save(newUser("dos@apunteia.com"))
        flushAndClear()

        val loaded = userRepository.findById(second.id!!).orElseThrow()
        loaded.email = "uno@apunteia.com"
        assertThrows<Exception> {
            userRepository.saveAndFlush(loaded)
        }
    }

    // ---------- DELETE ----------

    @Test
    fun `eliminar un usuario`() {
        val user = userRepository.save(newUser("borrar@apunteia.com"))
        flushAndClear()

        userRepository.deleteById(user.id!!)
        flushAndClear()

        assertFalse(userRepository.existsById(user.id!!))
        assertNull(userRepository.findByEmail("borrar@apunteia.com"))
    }

    @Test
    fun `no permite eliminar un rol que tiene usuarios`() {
        userRepository.save(newUser("dependiente@apunteia.com"))
        flushAndClear()

        assertThrows<Exception> {
            roleRepository.deleteById(student.id!!)
            roleRepository.flush()
        }
    }
}