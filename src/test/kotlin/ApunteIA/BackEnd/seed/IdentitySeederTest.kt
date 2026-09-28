package ApunteIA.BackEnd.seed

import ApunteIA.BackEnd.config.PasswordConfig
import ApunteIA.BackEnd.entity.AuthProvider
import ApunteIA.BackEnd.entity.Privilege
import ApunteIA.BackEnd.entity.Role
import ApunteIA.BackEnd.entity.UserStatus
import ApunteIA.BackEnd.repository.PrivilegeRepository
import ApunteIA.BackEnd.repository.RoleRepository
import ApunteIA.BackEnd.repository.UserRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.security.crypto.password.PasswordEncoder
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Verifica que la carga de datos de prueba de Identidad corre sobre una BD vacía y es idempotente. */
@DataJpaTest
@Import(IdentitySeeder::class, PasswordConfig::class)
class IdentitySeederTest @Autowired constructor(
    private val seeder: IdentitySeeder,
    private val roleRepository: RoleRepository,
    private val privilegeRepository: PrivilegeRepository,
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
) {

    @Test
    fun `carga roles, privilegios y usuarios semilla`() {
        seeder.seed()

        assertEquals(setOf(Role.STUDENT, Role.ADMIN), roleRepository.findAll().map { it.name }.toSet())
        assertEquals(
            setOf(Privilege.BASIC, Privilege.MANAGER, Privilege.FULL_ACCESS),
            privilegeRepository.findAll().map { it.name }.toSet()
        )
        assertEquals(5, userRepository.count())

        val admin = userRepository.findByEmail("admin@apunteia.com")!!
        assertEquals(Role.ADMIN, admin.role.name)
        assertEquals(Privilege.FULL_ACCESS, admin.privilege.name)

        assertEquals(UserStatus.BLOCKED, userRepository.findByEmail("bloqueado@apunteia.com")!!.status)

        val google = userRepository.findByEmail("google.user@gmail.com")!!
        assertEquals(AuthProvider.GOOGLE, google.authProvider)
        assertNull(google.passwordHash)
    }

    @Test
    fun `el usuario demo del FrontEnd queda con la contrasena hasheada`() {
        seeder.seed()

        val demo = userRepository.findByEmail("demo@apunteia.com")
        assertNotNull(demo)
        assertNotEquals("123456", demo.passwordHash)                   // nunca en texto plano
        assertTrue(passwordEncoder.matches("123456", demo.passwordHash))
    }

    @Test
    fun `correr la semilla dos veces no duplica datos`() {
        seeder.seed()
        seeder.seed()

        assertEquals(2, roleRepository.count())
        assertEquals(3, privilegeRepository.count())
        assertEquals(5, userRepository.count())
    }
}