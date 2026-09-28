package ApunteIA.BackEnd.seed

import ApunteIA.BackEnd.entity.Privilege
import ApunteIA.BackEnd.entity.Role
import ApunteIA.BackEnd.entity.User
import ApunteIA.BackEnd.entity.UserStatus
import ApunteIA.BackEnd.repository.CourseRepository
import ApunteIA.BackEnd.repository.EnrollmentRepository
import ApunteIA.BackEnd.repository.PrivilegeRepository
import ApunteIA.BackEnd.repository.RoleRepository
import ApunteIA.BackEnd.repository.SecurityLogRepository
import ApunteIA.BackEnd.repository.UserRepository
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.Import
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@DataJpaTest
@Import(AcademicSeeder::class)
class AcademicSeederTest @Autowired constructor(
    private val seeder: AcademicSeeder,
    private val roleRepository: RoleRepository,
    private val privilegeRepository: PrivilegeRepository,
    private val userRepository: UserRepository,
    private val courseRepository: CourseRepository,
    private val enrollmentRepository: EnrollmentRepository,
    private val securityLogRepository: SecurityLogRepository,
    private val entityManager: EntityManager,
) {
    @BeforeEach
    fun setUp() {
        val role = roleRepository.save(Role(Role.STUDENT, "Estudiante"))
        val privilege = privilegeRepository.save(Privilege(Privilege.BASIC, "Acceso básico"))
        userRepository.saveAll(
            listOf(
                User("demo@apunteia.com", "Usuario Demo", role, privilege),
                User("profe@apunteia.com", "Profesor", role, privilege),
                User("bloqueado@apunteia.com", "Usuario Bloqueado", role, privilege, status = UserStatus.BLOCKED),
                User("google.user@gmail.com", "Usuario Google", role, privilege),
            )
        )
    }

    @Test
    fun `carga cursos inscripciones cruzadas y logs de ejemplo`() {
        seeder.seed()
        entityManager.flush()
        entityManager.clear()

        assertEquals(setOf("EIF411", "EIF412", "EIF413"), courseRepository.findAll().map { it.code }.toSet())
        assertEquals(4, enrollmentRepository.count())
        assertEquals(3, securityLogRepository.count())
        assertEquals(2, enrollmentRepository.findAllByUser_Id(userRepository.findByEmail("demo@apunteia.com")!!.id!!).size)
        assertEquals(2, enrollmentRepository.findAllByCourse_Id(courseRepository.findByCode("EIF411")!!.id!!).size)
        assertNotNull(securityLogRepository.findAllByEventTypeOrderByOccurredAtDesc("LOGIN").firstOrNull())
        assertNotNull(securityLogRepository.findAllByEventTypeOrderByOccurredAtDesc("LOGIN_FAILED").firstOrNull())
    }

    @Test
    fun `cargar los datos varias veces no duplica cursos inscripciones ni logs`() {
        seeder.seed()
        seeder.seed()

        assertEquals(3, courseRepository.count())
        assertEquals(4, enrollmentRepository.count())
        assertEquals(3, securityLogRepository.count())
    }
}
