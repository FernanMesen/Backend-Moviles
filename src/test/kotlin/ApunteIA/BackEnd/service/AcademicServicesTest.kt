package ApunteIA.BackEnd.service

import ApunteIA.BackEnd.dto.course.CreateCourseRequest
import ApunteIA.BackEnd.dto.course.UpdateCourseRequest
import ApunteIA.BackEnd.dto.enrollment.CreateEnrollmentRequest
import ApunteIA.BackEnd.entity.Course
import ApunteIA.BackEnd.entity.Privilege
import ApunteIA.BackEnd.entity.Role
import ApunteIA.BackEnd.entity.SecurityLog
import ApunteIA.BackEnd.entity.User
import ApunteIA.BackEnd.exception.BadRequestException
import ApunteIA.BackEnd.exception.ConflictException
import ApunteIA.BackEnd.exception.NotFoundException
import ApunteIA.BackEnd.mapper.CourseMapper
import ApunteIA.BackEnd.mapper.EnrollmentMapper
import ApunteIA.BackEnd.mapper.SecurityLogMapper
import ApunteIA.BackEnd.repository.CourseRepository
import ApunteIA.BackEnd.repository.PrivilegeRepository
import ApunteIA.BackEnd.repository.RoleRepository
import ApunteIA.BackEnd.repository.SecurityLogRepository
import ApunteIA.BackEnd.repository.UserRepository
import ApunteIA.BackEnd.service.impl.CourseServiceImpl
import ApunteIA.BackEnd.service.impl.EnrollmentServiceImpl
import ApunteIA.BackEnd.service.impl.SecurityLogServiceImpl
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@DataJpaTest
@Import(
    CourseServiceImpl::class, EnrollmentServiceImpl::class, SecurityLogServiceImpl::class,
    CourseMapper::class, EnrollmentMapper::class, SecurityLogMapper::class,
)
class AcademicServicesTest @Autowired constructor(
    private val courses: CourseService,
    private val enrollments: EnrollmentService,
    private val logs: SecurityLogService,
    private val courseRepository: CourseRepository,
    private val userRepository: UserRepository,
    private val roleRepository: RoleRepository,
    private val privilegeRepository: PrivilegeRepository,
    private val logRepository: SecurityLogRepository,
    private val entityManager: EntityManager,
) {
    private lateinit var user: User
    private lateinit var otherUser: User
    private lateinit var course: Course
    private lateinit var otherCourse: Course

    @BeforeEach
    fun setUp() {
        val role = roleRepository.save(Role(Role.STUDENT))
        val privilege = privilegeRepository.save(Privilege(Privilege.BASIC))
        user = userRepository.saveAndFlush(User("ana@test.com", "Ana", role, privilege))
        otherUser = userRepository.saveAndFlush(User("luis@test.com", "Luis", role, privilege))
        course = courseRepository.saveAndFlush(Course("EIF411", "Móviles"))
        otherCourse = courseRepository.saveAndFlush(Course("EIF412", "Bases de datos"))
    }

    @Test
    fun `crear y actualizar curso normaliza sus campos y permite conservar su codigo`() {
        val created = courses.create(CreateCourseRequest(" EIF413 ", " Curso ", " Descripción "))
        assertEquals("EIF413", created.code)
        assertEquals("Curso", created.name)
        assertEquals("Descripción", created.description)
        val updated = courses.update(created.id, UpdateCourseRequest(" EIF413 ", " Nuevo "))
        assertEquals("Nuevo", updated.name)
        assertNull(updated.description)
        assertEquals(updated, courses.findById(created.id))
        assertEquals(courses.findAll().map { it.id }.sorted(), courses.findAll().map { it.id })
    }

    @Test
    fun `crear curso con codigo duplicado normalizado produce conflicto`() {
        assertThrows<ConflictException> { courses.create(CreateCourseRequest(" EIF411 ", "Duplicado")) }
    }

    @Test
    fun `actualizar codigo usado por otro curso produce conflicto sin cambiarlo`() {
        assertThrows<ConflictException> {
            courses.update(course.id!!, UpdateCourseRequest(" EIF412 ", "Duplicado"))
        }
        assertEquals("EIF411", courses.findById(course.id!!).code)
    }

    @Test
    fun `operaciones sobre curso inexistente producen not found`() {
        assertEquals("No existe un curso con id 999999", assertThrows<NotFoundException> {
            courses.findById(999999)
        }.message)
        assertThrows<NotFoundException> { courses.update(999999, UpdateCourseRequest("EIF411", "Curso")) }
        assertThrows<NotFoundException> { courses.delete(999999) }
    }

    @Test
    fun `eliminar curso sin relaciones lo elimina`() {
        courses.delete(course.id!!)
        assertThrows<NotFoundException> { courses.findById(course.id!!) }
    }

    @Test
    fun `eliminar curso inscrito propaga restriccion de integridad`() {
        enrollments.create(CreateEnrollmentRequest(user.id, course.id))
        entityManager.clear()
        assertThrows<DataIntegrityViolationException> { courses.delete(course.id!!) }
    }

    @Test
    fun `crear inscripcion resuelve relaciones y rechaza duplicados`() {
        val created = enrollments.create(CreateEnrollmentRequest(user.id, course.id))
        assertEquals(user.id, created.userId)
        assertEquals("Ana", created.userFullName)
        assertEquals("EIF411", created.courseCode)
        assertNotNull(created.enrolledAt)
        assertThrows<ConflictException> { enrollments.create(CreateEnrollmentRequest(user.id, course.id)) }
    }

    @Test
    fun `crear inscripcion valida ids y relaciones inexistentes`() {
        assertThrows<BadRequestException> { enrollments.create(CreateEnrollmentRequest()) }
        assertThrows<BadRequestException> { enrollments.create(CreateEnrollmentRequest(0, course.id)) }
        assertThrows<BadRequestException> { enrollments.create(CreateEnrollmentRequest(user.id, -1)) }
        assertThrows<NotFoundException> { enrollments.create(CreateEnrollmentRequest(999999, course.id)) }
        assertThrows<NotFoundException> { enrollments.create(CreateEnrollmentRequest(user.id, 999999)) }
    }

    @Test
    fun `listar inscripciones combina filtros y mapea relaciones lazy`() {
        val target = enrollments.create(CreateEnrollmentRequest(user.id, course.id))
        enrollments.create(CreateEnrollmentRequest(user.id, otherCourse.id))
        enrollments.create(CreateEnrollmentRequest(otherUser.id, course.id))
        entityManager.clear()
        assertEquals(listOf(target), enrollments.findAll(user.id, course.id))
        assertEquals(2, enrollments.findAll(userId = user.id).size)
        assertEquals(2, enrollments.findAll(courseId = course.id).size)
        assertEquals(3, enrollments.findAll().size)
        assertEquals(target, enrollments.findById(target.id))
    }

    @Test
    fun `eliminar inscripcion conserva usuario y curso y rechaza ids inexistentes`() {
        val created = enrollments.create(CreateEnrollmentRequest(user.id, course.id))
        enrollments.delete(created.id)
        assertTrue(userRepository.existsById(user.id!!))
        assertTrue(courseRepository.existsById(course.id!!))
        assertThrows<NotFoundException> { enrollments.findById(created.id) }
        assertThrows<NotFoundException> { enrollments.delete(created.id) }
    }

    @Test
    fun `listar logs combina filtros normaliza evento y maneja anonimos`() {
        val login = logRepository.saveAndFlush(SecurityLog("LOGIN", user))
        logRepository.saveAndFlush(SecurityLog("LOGOUT", user))
        logRepository.saveAndFlush(SecurityLog("LOGIN", otherUser))
        val anonymous = logRepository.saveAndFlush(SecurityLog("LOGIN"))
        entityManager.clear()
        assertEquals(listOf(login.id), logs.findAll(user.id, " LOGIN ").map { it.id })
        assertEquals(2, logs.findAll(userId = user.id).size)
        assertEquals(3, logs.findAll(eventType = "LOGIN").size)
        val result = logs.findAll(eventType = " LOGIN ", anonymousOnly = true).single()
        assertEquals(anonymous.id, result.id)
        assertNull(result.userId)
        assertNull(result.userFullName)
        assertTrue(logs.findAll(user.id, anonymousOnly = true).isEmpty())
        assertEquals(listOf("Ana", "Ana", "Luis", null).sortedBy { it },
            logs.findAll().map { it.userFullName }.sortedBy { it })
        val all = logs.findAll()
        assertEquals(all.sortedByDescending { it.occurredAt }.map { it.id }, all.map { it.id })
        assertEquals(result, logs.findById(anonymous.id!!))
        assertThrows<NotFoundException> { logs.findById(999999) }
    }
}
