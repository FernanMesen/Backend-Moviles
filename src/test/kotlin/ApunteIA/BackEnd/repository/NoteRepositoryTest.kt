package ApunteIA.BackEnd.repository

import ApunteIA.BackEnd.entity.Course
import ApunteIA.BackEnd.entity.Note
import ApunteIA.BackEnd.entity.Privilege
import ApunteIA.BackEnd.entity.ProcessingStatus
import ApunteIA.BackEnd.entity.Role
import ApunteIA.BackEnd.entity.User
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

/** Pruebas de persistencia de notas sobre H2, con rollback independiente por prueba. */
@DataJpaTest
class NoteRepositoryTest @Autowired constructor(
    private val noteRepository: NoteRepository,
    private val roleRepository: RoleRepository,
    private val privilegeRepository: PrivilegeRepository,
    private val userRepository: UserRepository,
    private val courseRepository: CourseRepository,
    private val entityManager: EntityManager,
) {
    private lateinit var student: Role
    private lateinit var basic: Privilege
    private lateinit var user: User
    private lateinit var course: Course

    @BeforeEach
    fun setUp() {
        student = roleRepository.save(Role(Role.STUDENT, "Estudiante"))
        basic = privilegeRepository.save(Privilege(Privilege.BASIC, "Acceso básico"))
        user = userRepository.save(newUser("estudiante@apunteia.com"))
        course = courseRepository.save(Course(code = "EIF411", name = "Backend Móvil"))
    }

    private fun newUser(email: String) =
        User(email = email, fullName = "Estudiante de Prueba", role = student, privilege = basic)

    private fun newNote(title: String, user: User = this.user, course: Course = this.course) =
        Note(title = title, content = "Contenido de $title", user = user, course = course)

    /** Fuerza la escritura y evita que las lecturas reutilicen entidades en memoria. */
    private fun flushAndClear() {
        entityManager.flush()
        entityManager.clear()
    }

    @Test
    fun `guarda y recupera una nota con relaciones estado y timestamps`() {
        val saved = noteRepository.save(
            Note(title = "Persistencia JPA", content = "Apuntes sobre entidades y relaciones", user = user, course = course)
        )
        flushAndClear()

        val id = assertNotNull(saved.id)
        val found = noteRepository.findById(id).orElseThrow()
        assertEquals(id, found.id)
        assertEquals("Persistencia JPA", found.title)
        assertEquals("Apuntes sobre entidades y relaciones", found.content)
        assertEquals(user.id, found.user.id)
        assertEquals("estudiante@apunteia.com", found.user.email)
        assertEquals(course.id, found.course.id)
        assertEquals("EIF411", found.course.code)
        assertEquals(ProcessingStatus.PENDING, found.processingStatus)
        assertNotNull(found.createdAt)
        assertNotNull(found.updatedAt)
        assertFalse(found.updatedAt!!.isBefore(found.createdAt))
    }

    @Test
    fun `filtra por usuario sin mezclar notas de otros usuarios`() {
        val otherUser = userRepository.save(newUser("otra@apunteia.com"))
        val first = noteRepository.save(newNote("Primera nota"))
        val second = noteRepository.save(newNote("Segunda nota"))
        val other = noteRepository.save(newNote("Nota ajena", user = otherUser))
        flushAndClear()

        assertEquals(setOf(first.id, second.id), noteRepository.findAllByUser_Id(user.id!!).map { it.id }.toSet())
        assertEquals(listOf(other.id), noteRepository.findAllByUser_Id(otherUser.id!!).map { it.id })
    }

    @Test
    fun `filtra por curso sin mezclar notas de otros cursos`() {
        val otherCourse = courseRepository.save(Course(code = "EIF400", name = "Otro curso"))
        val first = noteRepository.save(newNote("Primera nota"))
        val second = noteRepository.save(newNote("Segunda nota"))
        val other = noteRepository.save(newNote("Nota de otro curso", course = otherCourse))
        flushAndClear()

        assertEquals(setOf(first.id, second.id), noteRepository.findAllByCourse_Id(course.id!!).map { it.id }.toSet())
        assertEquals(listOf(other.id), noteRepository.findAllByCourse_Id(otherCourse.id!!).map { it.id })
    }

    @Test
    fun `filtra por usuario y curso exigiendo ambas condiciones`() {
        val otherUser = userRepository.save(newUser("otra@apunteia.com"))
        val otherCourse = courseRepository.save(Course(code = "EIF400", name = "Otro curso"))
        val first = noteRepository.save(newNote("Primera coincidencia"))
        val second = noteRepository.save(newNote("Segunda coincidencia"))
        noteRepository.save(newNote("Solo coincide usuario", course = otherCourse))
        noteRepository.save(newNote("Solo coincide curso", user = otherUser))
        noteRepository.save(newNote("Ninguna coincidencia", user = otherUser, course = otherCourse))
        flushAndClear()

        val found = noteRepository.findAllByUser_IdAndCourse_Id(user.id!!, course.id!!)
        assertEquals(setOf(first.id, second.id), found.map { it.id }.toSet())
    }

    @Test
    fun `persiste la transicion de pendiente a procesamiento y completado`() {
        val saved = noteRepository.save(newNote("Nota en procesamiento"))
        flushAndClear()

        val id = assertNotNull(saved.id)
        val pending = noteRepository.findById(id).orElseThrow()
        assertEquals(ProcessingStatus.PENDING, pending.processingStatus)
        val createdAt = assertNotNull(pending.createdAt)
        val initialUpdatedAt = assertNotNull(pending.updatedAt)

        pending.processingStatus = ProcessingStatus.PROCESSING
        noteRepository.save(pending)
        flushAndClear()

        val processing = noteRepository.findById(id).orElseThrow()
        assertEquals(ProcessingStatus.PROCESSING, processing.processingStatus)
        assertEquals(createdAt, processing.createdAt)
        val processingUpdatedAt = assertNotNull(processing.updatedAt)
        assertFalse(processingUpdatedAt.isBefore(initialUpdatedAt))

        processing.processingStatus = ProcessingStatus.COMPLETED
        noteRepository.save(processing)
        flushAndClear()

        val completed = noteRepository.findById(id).orElseThrow()
        assertEquals(ProcessingStatus.COMPLETED, completed.processingStatus)
        assertEquals(createdAt, completed.createdAt)
        assertFalse(assertNotNull(completed.updatedAt).isBefore(processingUpdatedAt))
    }

    @Test
    fun `filtra notas por estado de procesamiento`() {
        val pending = noteRepository.save(newNote("Pendiente"))
        val processing = noteRepository.save(newNote("En proceso").apply { processingStatus = ProcessingStatus.PROCESSING })
        val completed = noteRepository.save(newNote("Completada").apply { processingStatus = ProcessingStatus.COMPLETED })
        val failed = noteRepository.save(newNote("Fallida").apply { processingStatus = ProcessingStatus.FAILED })
        val anotherFailed = noteRepository.save(newNote("Otra fallida").apply { processingStatus = ProcessingStatus.FAILED })
        flushAndClear()

        assertEquals(listOf(pending.id), noteRepository.findAllByProcessingStatus(ProcessingStatus.PENDING).map { it.id })
        assertEquals(listOf(processing.id), noteRepository.findAllByProcessingStatus(ProcessingStatus.PROCESSING).map { it.id })
        assertEquals(listOf(completed.id), noteRepository.findAllByProcessingStatus(ProcessingStatus.COMPLETED).map { it.id })
        assertEquals(setOf(failed.id, anotherFailed.id), noteRepository.findAllByProcessingStatus(ProcessingStatus.FAILED).map { it.id }.toSet())
    }
}
