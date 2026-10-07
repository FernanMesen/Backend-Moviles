package ApunteIA.BackEnd.seed

import ApunteIA.BackEnd.entity.Course
import ApunteIA.BackEnd.entity.Privilege
import ApunteIA.BackEnd.entity.Role
import ApunteIA.BackEnd.entity.User
import ApunteIA.BackEnd.repository.AssessmentAttemptRepository
import ApunteIA.BackEnd.repository.AssessmentRepository
import ApunteIA.BackEnd.repository.CourseRepository
import ApunteIA.BackEnd.repository.PrivilegeRepository
import ApunteIA.BackEnd.repository.RoleRepository
import ApunteIA.BackEnd.repository.UserRepository
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.Import
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@DataJpaTest
@Import(AssessmentSeeder::class)
class AssessmentSeederTest @Autowired constructor(
    private val seeder: AssessmentSeeder,
    private val assessmentRepository: AssessmentRepository,
    private val attemptRepository: AssessmentAttemptRepository,
    private val courseRepository: CourseRepository,
    private val roleRepository: RoleRepository,
    private val privilegeRepository: PrivilegeRepository,
    private val userRepository: UserRepository,
    private val entityManager: EntityManager,
) {
    @BeforeEach
    fun setUp() {
        val role = roleRepository.save(Role(Role.STUDENT, "Estudiante"))
        val privilege = privilegeRepository.save(Privilege(Privilege.BASIC, "Acceso básico"))
        userRepository.saveAll(
            listOf(
                User("demo@apunteia.com", "Usuario Demo", role, privilege),
                User("google.user@gmail.com", "Usuario Google", role, privilege),
                User("profe@apunteia.com", "Profesor", role, privilege),
            )
        )
        courseRepository.saveAll(
            listOf(
                Course("EIF411", "Backend para plataformas móviles"),
                Course("EIF412", "Bases de datos"),
                Course("EIF413", "Programación distribuida"),
            )
        )
    }

    private fun flushAndClear() {
        entityManager.flush()
        entityManager.clear()
    }

    @Test
    fun `crea las evaluaciones con sus preguntas y creador`() {
        seeder.seed()
        flushAndClear()

        assertEquals(3, assessmentRepository.count())
        val backend = assessmentRepository.findByTitle("Introducción al Backend")
        assertNotNull(backend)
        assertEquals(3, backend.questions.size)
        assertEquals("profe@apunteia.com", backend.createdBy?.email)
        assertEquals(2, assessmentRepository.findByTitle("Programación distribuida")!!.questions.size)
    }

    @Test
    fun `crea los intentos con el puntaje calculado`() {
        seeder.seed()
        flushAndClear()

        assertEquals(5, attemptRepository.count())
        val demo = userRepository.findByEmail("demo@apunteia.com")!!
        val attempts = attemptRepository.findAllByUser_IdOrderByCompletedAtDesc(demo.id!!)
        assertEquals(3, attempts.size)
        assertEquals(BigDecimal("66.67"), attempts.first().score)
        assertEquals(BigDecimal("100.00"), attempts.last().score)
    }

    @Test
    fun `cargar los datos varias veces no duplica evaluaciones ni intentos`() {
        seeder.seed()
        flushAndClear()
        seeder.seed()
        flushAndClear()

        assertEquals(3, assessmentRepository.count())
        assertEquals(5, attemptRepository.count())
    }

    @Test
    fun `falla si falta un usuario requerido`() {
        userRepository.deleteAll()
        flushAndClear()

        assertThrows<IllegalStateException> { seeder.seed() }
    }
}