package ApunteIA.BackEnd.repository

import ApunteIA.BackEnd.entity.Course
import ApunteIA.BackEnd.entity.Enrollment
import ApunteIA.BackEnd.entity.Privilege
import ApunteIA.BackEnd.entity.Role
import ApunteIA.BackEnd.entity.User
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.dao.DataIntegrityViolationException
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Pruebas de persistencia de cursos e inscripciones, incluida la restricción contra duplicados. */
@DataJpaTest
class AcademicRepositoryTest @Autowired constructor(
    private val courseRepository: CourseRepository,
    private val enrollmentRepository: EnrollmentRepository,
    private val roleRepository: RoleRepository,
    private val privilegeRepository: PrivilegeRepository,
    private val userRepository: UserRepository,
    private val entityManager: EntityManager,
) {
    private lateinit var user: User
    private lateinit var course: Course

    @BeforeEach
    fun setUp() {
        val role = roleRepository.save(Role(Role.STUDENT, "Estudiante"))
        val privilege = privilegeRepository.save(Privilege(Privilege.BASIC, "Acceso básico"))
        user = userRepository.save(
            User(
                email = "estudiante@apunteia.com",
                fullName = "Estudiante de Prueba",
                role = role,
                privilege = privilege,
            )
        )
        course = courseRepository.save(Course(code = "EIF411", name = "Backend Móvil"))
    }

    private fun flushAndClear() {
        entityManager.flush()
        entityManager.clear()
    }

    @Test
    fun `guarda y busca un curso por codigo`() {
        flushAndClear()

        val found = courseRepository.findByCode("EIF411")

        assertNotNull(found)
        assertNotNull(found.id)
        assertEquals("Backend Móvil", found.name)
        assertTrue(courseRepository.existsByCode("EIF411"))
    }

    @Test
    fun `guarda una inscripcion y consulta sus relaciones`() {
        val enrollment = enrollmentRepository.save(Enrollment(user = user, course = course))
        flushAndClear()

        val found = enrollmentRepository.findById(enrollment.id!!).orElseThrow()
        assertEquals(user.id, found.user.id)
        assertEquals(course.id, found.course.id)
        assertNotNull(found.enrolledAt)
        assertEquals(1, enrollmentRepository.findAllByUser_Id(user.id!!).size)
        assertEquals(1, enrollmentRepository.findAllByCourse_Id(course.id!!).size)
        assertTrue(enrollmentRepository.existsByUser_IdAndCourse_Id(user.id!!, course.id!!))
    }

    @Test
    fun `rechaza una inscripcion duplicada para el mismo usuario y curso`() {
        enrollmentRepository.saveAndFlush(Enrollment(user = user, course = course))

        assertThrows<DataIntegrityViolationException> {
            enrollmentRepository.saveAndFlush(Enrollment(user = user, course = course))
        }
    }
}
