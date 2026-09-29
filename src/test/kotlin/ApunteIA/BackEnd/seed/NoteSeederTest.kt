package ApunteIA.BackEnd.seed

import ApunteIA.BackEnd.entity.Course
import ApunteIA.BackEnd.entity.Privilege
import ApunteIA.BackEnd.entity.ProcessingStatus
import ApunteIA.BackEnd.entity.Role
import ApunteIA.BackEnd.entity.User
import ApunteIA.BackEnd.repository.CourseRepository
import ApunteIA.BackEnd.repository.NoteRepository
import ApunteIA.BackEnd.repository.PrivilegeRepository
import ApunteIA.BackEnd.repository.RoleRepository
import ApunteIA.BackEnd.repository.UserRepository
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.Import
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@DataJpaTest
@Import(NoteSeeder::class)
class NoteSeederTest @Autowired constructor(
    private val seeder: NoteSeeder,
    private val noteRepository: NoteRepository,
    private val roleRepository: RoleRepository,
    private val privilegeRepository: PrivilegeRepository,
    private val userRepository: UserRepository,
    private val courseRepository: CourseRepository,
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
    fun `carga notas con distintos estados y relaciones existentes`() {
        val userIds = userRepository.findAll().map { it.id }.toSet()
        val courseIds = courseRepository.findAll().map { it.id }.toSet()
        seeder.seed()
        flushAndClear()

        val notes = noteRepository.findAll()
        assertEquals(4, notes.size)
        assertEquals(
            mapOf(
                "Entidades y relaciones JPA" to Triple("demo@apunteia.com", "EIF411", ProcessingStatus.COMPLETED),
                "Normalización de bases de datos" to Triple("demo@apunteia.com", "EIF412", ProcessingStatus.PENDING),
                "Ciclo de vida de una aplicación móvil" to Triple("google.user@gmail.com", "EIF411", ProcessingStatus.PROCESSING),
                "Consenso en sistemas distribuidos" to Triple("profe@apunteia.com", "EIF413", ProcessingStatus.FAILED),
            ),
            notes.associate { it.title to Triple(it.user.email, it.course.code, it.processingStatus) },
        )
        notes.forEach {
            assertNotNull(it.id)
            assertTrue(it.content.isNotBlank())
            assertTrue(it.user.id in userIds)
            assertTrue(it.course.id in courseIds)
        }
        assertEquals(userIds, userRepository.findAll().map { it.id }.toSet())
        assertEquals(courseIds, courseRepository.findAll().map { it.id }.toSet())
    }

    @Test
    fun `cargar dos veces conserva las notas y los estados modificados`() {
        seeder.seed()
        flushAndClear()
        val originalIds = noteRepository.findAll().map { it.id }.toSet()
        val pending = noteRepository.findAllByProcessingStatus(ProcessingStatus.PENDING).single()
        val pendingId = assertNotNull(pending.id)
        pending.processingStatus = ProcessingStatus.COMPLETED
        noteRepository.save(pending)
        flushAndClear()

        seeder.seed()
        flushAndClear()

        assertEquals(4, noteRepository.count())
        assertEquals(originalIds, noteRepository.findAll().map { it.id }.toSet())
        assertEquals(ProcessingStatus.COMPLETED, noteRepository.findById(pendingId).orElseThrow().processingStatus)
        assertEquals(3, userRepository.count())
        assertEquals(3, courseRepository.count())
    }
}
