package ApunteIA.BackEnd.repository

import ApunteIA.BackEnd.entity.Course
import ApunteIA.BackEnd.entity.Enrollment
import ApunteIA.BackEnd.entity.Privilege
import ApunteIA.BackEnd.entity.Role
import ApunteIA.BackEnd.entity.SecurityLog
import ApunteIA.BackEnd.entity.User
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Pruebas de inserción, búsqueda y actualización de Course, Enrollment y SecurityLog. */
@DataJpaTest
class AcademicCrudTest @Autowired constructor(
    private val courseRepository: CourseRepository,
    private val enrollmentRepository: EnrollmentRepository,
    private val securityLogRepository: SecurityLogRepository,
    private val roleRepository: RoleRepository,
    private val privilegeRepository: PrivilegeRepository,
    private val userRepository: UserRepository,
    private val entityManager: EntityManager,
) {
    private lateinit var role: Role
    private lateinit var privilege: Privilege
    private lateinit var ana: User
    private lateinit var luis: User
    private lateinit var backend: Course
    private lateinit var databases: Course

    @BeforeEach
    fun setUp() {
        role = roleRepository.save(Role(Role.STUDENT, "Estudiante"))
        privilege = privilegeRepository.save(Privilege(Privilege.BASIC, "Acceso básico"))
        ana = userRepository.save(newUser("ana@apunteia.com"))
        luis = userRepository.save(newUser("luis@apunteia.com"))
        backend = courseRepository.save(Course(code = "EIF411", name = "Backend Móvil", description = "Persistencia"))
        databases = courseRepository.save(Course(code = "EIF412", name = "Bases de datos"))
    }

    private fun newUser(email: String) =
        User(email = email, fullName = "Usuario Prueba", role = role, privilege = privilege)

    private fun flushAndClear() {
        entityManager.flush()
        entityManager.clear()
    }

    // ---------- COURSE ----------

    @Test
    fun `insertar un curso guarda todos sus campos`() {
        val saved = courseRepository.save(
            Course(code = "EIF413", name = "Programación distribuida", description = "Sistemas distribuidos")
        )
        flushAndClear()

        val found = courseRepository.findById(saved.id!!).orElseThrow()
        assertEquals("EIF413", found.code)
        assertEquals("Programación distribuida", found.name)
        assertEquals("Sistemas distribuidos", found.description)
    }

    @Test
    fun `insertar un curso sin descripcion`() {
        val saved = courseRepository.save(Course(code = "EIF414", name = "Curso sin descripción"))
        flushAndClear()

        assertNull(courseRepository.findById(saved.id!!).orElseThrow().description)
    }

    @Test
    fun `listar y contar cursos`() {
        flushAndClear()

        assertEquals(2, courseRepository.count())
        assertEquals(setOf("EIF411", "EIF412"), courseRepository.findAll().map { it.code }.toSet())
    }

    @Test
    fun `buscar un curso inexistente`() {
        assertNull(courseRepository.findByCode("NO-EXISTE"))
        assertFalse(courseRepository.existsByCode("NO-EXISTE"))
        assertTrue(courseRepository.findById(999_999L).isEmpty)
    }

    @Test
    fun `actualizar nombre y descripcion de un curso`() {
        flushAndClear()

        val course = courseRepository.findByCode("EIF412")!!
        course.name = "Bases de datos avanzadas"
        course.description = "Modelado y optimización"
        courseRepository.save(course)
        flushAndClear()

        val found = courseRepository.findByCode("EIF412")!!
        assertEquals("Bases de datos avanzadas", found.name)
        assertEquals("Modelado y optimización", found.description)
    }

    @Test
    fun `no permite dos cursos con el mismo codigo`() {
        assertThrows<Exception> {
            courseRepository.saveAndFlush(Course(code = "EIF411", name = "Duplicado"))
        }
    }

    // ---------- ENROLLMENT ----------

    @Test
    fun `un usuario se inscribe en varios cursos`() {
        enrollmentRepository.saveAll(
            listOf(Enrollment(user = ana, course = backend), Enrollment(user = ana, course = databases))
        )
        flushAndClear()

        val enrollments = enrollmentRepository.findAllByUser_Id(ana.id!!)
        assertEquals(2, enrollments.size)
        assertEquals(setOf("EIF411", "EIF412"), enrollments.map { it.course.code }.toSet())
    }

    @Test
    fun `un curso tiene varios usuarios inscritos`() {
        enrollmentRepository.saveAll(
            listOf(Enrollment(user = ana, course = backend), Enrollment(user = luis, course = backend))
        )
        flushAndClear()

        val enrollments = enrollmentRepository.findAllByCourse_Id(backend.id!!)
        assertEquals(setOf("ana@apunteia.com", "luis@apunteia.com"), enrollments.map { it.user.email }.toSet())
        assertTrue(enrollmentRepository.findAllByCourse_Id(databases.id!!).isEmpty())
    }

    @Test
    fun `existe inscripcion solo para la pareja correcta`() {
        enrollmentRepository.save(Enrollment(user = ana, course = backend))
        flushAndClear()

        assertTrue(enrollmentRepository.existsByUser_IdAndCourse_Id(ana.id!!, backend.id!!))
        assertFalse(enrollmentRepository.existsByUser_IdAndCourse_Id(ana.id!!, databases.id!!))
        assertFalse(enrollmentRepository.existsByUser_IdAndCourse_Id(luis.id!!, backend.id!!))
    }

    @Test
    fun `la inscripcion guarda su fecha automaticamente`() {
        val saved = enrollmentRepository.save(Enrollment(user = luis, course = databases))
        flushAndClear()

        assertNotNull(enrollmentRepository.findById(saved.id!!).orElseThrow().enrolledAt)
    }

    @Test
    fun `eliminar una inscripcion no borra al usuario ni al curso`() {
        val saved = enrollmentRepository.save(Enrollment(user = ana, course = backend))
        flushAndClear()

        enrollmentRepository.deleteById(saved.id!!)
        flushAndClear()

        assertFalse(enrollmentRepository.existsByUser_IdAndCourse_Id(ana.id!!, backend.id!!))
        assertTrue(userRepository.existsById(ana.id!!))
        assertTrue(courseRepository.existsById(backend.id!!))
    }

    // ---------- SECURITY LOG ----------

    @Test
    fun `insertar un log guarda todos sus campos`() {
        val saved = securityLogRepository.save(
            SecurityLog(eventType = "LOGIN", user = ana, details = "Inicio de sesión", ipAddress = "10.0.0.1")
        )
        flushAndClear()

        val found = securityLogRepository.findById(saved.id!!).orElseThrow()
        assertEquals("LOGIN", found.eventType)
        assertEquals(ana.id, found.user?.id)
        assertEquals("Inicio de sesión", found.details)
        assertEquals("10.0.0.1", found.ipAddress)
        assertNotNull(found.occurredAt)
    }

    @Test
    fun `insertar un log anonimo sin usuario`() {
        securityLogRepository.save(SecurityLog(eventType = "LOGIN_FAILED", details = "Email desconocido"))
        securityLogRepository.save(SecurityLog(eventType = "LOGIN", user = ana))
        flushAndClear()

        val anonymous = securityLogRepository.findAllByUserIsNullOrderByOccurredAtDesc()
        assertEquals(1, anonymous.size)
        assertEquals("LOGIN_FAILED", anonymous.first().eventType)
    }

    @Test
    fun `filtrar logs por usuario y por tipo de evento`() {
        securityLogRepository.saveAll(
            listOf(
                SecurityLog(eventType = "LOGIN", user = ana),
                SecurityLog(eventType = "PASSWORD_CHANGED", user = ana),
                SecurityLog(eventType = "LOGIN", user = luis),
            )
        )
        flushAndClear()

        assertEquals(
            setOf("LOGIN", "PASSWORD_CHANGED"),
            securityLogRepository.findAllByUser_IdOrderByOccurredAtDesc(ana.id!!).map { it.eventType }.toSet(),
        )
        assertEquals(
            setOf("ana@apunteia.com", "luis@apunteia.com"),
            securityLogRepository.findAllByEventTypeOrderByOccurredAtDesc("LOGIN").map { it.user?.email }.toSet(),
        )
        assertTrue(securityLogRepository.findAllByEventTypeOrderByOccurredAtDesc("ACCOUNT_DELETED").isEmpty())
    }

    @Test
    fun `actualizar los detalles de un log`() {
        val saved = securityLogRepository.save(SecurityLog(eventType = "ROLE_CHANGED", user = luis))
        flushAndClear()

        val loaded = securityLogRepository.findById(saved.id!!).orElseThrow()
        loaded.details = "STUDENT -> ADMIN"
        securityLogRepository.save(loaded)
        flushAndClear()

        assertEquals("STUDENT -> ADMIN", securityLogRepository.findById(saved.id!!).orElseThrow().details)
    }
}
