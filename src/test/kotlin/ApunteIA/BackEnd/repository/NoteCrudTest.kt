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
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Pruebas de inserción, búsqueda y actualización de Note. */
@DataJpaTest
class NoteCrudTest @Autowired constructor(
    private val noteRepository: NoteRepository,
    private val roleRepository: RoleRepository,
    private val privilegeRepository: PrivilegeRepository,
    private val userRepository: UserRepository,
    private val courseRepository: CourseRepository,
    private val entityManager: EntityManager,
) {
    private lateinit var user: User
    private lateinit var backend: Course
    private lateinit var databases: Course

    @BeforeEach
    fun setUp() {
        val role = roleRepository.save(Role(Role.STUDENT, "Estudiante"))
        val privilege = privilegeRepository.save(Privilege(Privilege.BASIC, "Acceso básico"))
        user = userRepository.save(
            User(email = "estudiante@apunteia.com", fullName = "Estudiante", role = role, privilege = privilege)
        )
        backend = courseRepository.save(Course(code = "EIF411", name = "Backend Móvil"))
        databases = courseRepository.save(Course(code = "EIF412", name = "Bases de datos"))
    }

    private fun newNote(title: String, course: Course = backend) =
        Note(title = title, content = "Contenido de $title", user = user, course = course)

    private fun flushAndClear() {
        entityManager.flush()
        entityManager.clear()
    }

    // ---------- INSERT ----------

    @Test
    fun `insertar una nota le asigna id y estado PENDING`() {
        val saved = noteRepository.save(newNote("Primera nota"))
        flushAndClear()

        assertNotNull(saved.id)
        val found = noteRepository.findById(saved.id!!).orElseThrow()
        assertEquals("Primera nota", found.title)
        assertEquals("Contenido de Primera nota", found.content)
        assertEquals(ProcessingStatus.PENDING, found.processingStatus)
        assertNotNull(found.createdAt)
    }

    @Test
    fun `insertar una nota ya con un estado distinto`() {
        val saved = noteRepository.save(newNote("Ya procesada").apply { processingStatus = ProcessingStatus.COMPLETED })
        flushAndClear()

        assertEquals(ProcessingStatus.COMPLETED, noteRepository.findById(saved.id!!).orElseThrow().processingStatus)
    }

    @Test
    fun `insertar varias notas con saveAll`() {
        noteRepository.saveAll(listOf(newNote("Nota 1"), newNote("Nota 2"), newNote("Nota 3", databases)))
        flushAndClear()

        assertEquals(3, noteRepository.count())
        assertEquals(3, noteRepository.findAll().size)
    }

    @Test
    fun `insertar una nota con contenido largo lo guarda completo`() {
        val longContent = "Apunte escaneado. ".repeat(500)
        val saved = noteRepository.save(
            Note(title = "Apunte largo", content = longContent, user = user, course = backend)
        )
        flushAndClear()

        assertEquals(longContent, noteRepository.findById(saved.id!!).orElseThrow().content)
    }

    @Test
    fun `no permite una nota con un curso que no existe`() {
        val unsavedCourse = Course(code = "FANTASMA", name = "No guardado")
        assertThrows<Exception> {
            noteRepository.saveAndFlush(newNote("Nota huérfana", unsavedCourse))
        }
    }

    // ---------- FIND ----------

    @Test
    fun `buscar una nota inexistente devuelve vacio`() {
        assertTrue(noteRepository.findById(999_999L).isEmpty)
        assertFalse(noteRepository.existsById(999_999L))
    }

    @Test
    fun `filtros sin coincidencias devuelven lista vacia`() {
        noteRepository.save(newNote("Solo backend"))
        flushAndClear()

        assertTrue(noteRepository.findAllByCourse_Id(databases.id!!).isEmpty())
        assertTrue(noteRepository.findAllByUser_IdAndCourse_Id(user.id!!, databases.id!!).isEmpty())
        assertTrue(noteRepository.findAllByProcessingStatus(ProcessingStatus.FAILED).isEmpty())
    }

    @Test
    fun `la nota recuperada conoce a su usuario y su curso`() {
        val saved = noteRepository.save(newNote("Con relaciones", databases))
        flushAndClear()

        val found = noteRepository.findById(saved.id!!).orElseThrow()
        assertEquals("estudiante@apunteia.com", found.user.email)
        assertEquals("EIF412", found.course.code)
    }

    // ---------- UPDATE ----------

    @Test
    fun `actualizar titulo y contenido de una nota`() {
        val saved = noteRepository.save(newNote("Título viejo"))
        flushAndClear()

        val loaded = noteRepository.findById(saved.id!!).orElseThrow()
        loaded.title = "Título nuevo"
        loaded.content = "Contenido corregido"
        noteRepository.save(loaded)
        flushAndClear()

        val found = noteRepository.findById(saved.id!!).orElseThrow()
        assertEquals("Título nuevo", found.title)
        assertEquals("Contenido corregido", found.content)
        assertFalse(found.updatedAt!!.isBefore(found.createdAt))
    }

    @Test
    fun `mover una nota a otro curso`() {
        val saved = noteRepository.save(newNote("Mal clasificada", backend))
        flushAndClear()

        val loaded = noteRepository.findById(saved.id!!).orElseThrow()
        loaded.course = courseRepository.findByCode("EIF412")!!
        noteRepository.save(loaded)
        flushAndClear()

        assertTrue(noteRepository.findAllByCourse_Id(backend.id!!).isEmpty())
        assertEquals(listOf(saved.id), noteRepository.findAllByCourse_Id(databases.id!!).map { it.id })
    }

    @Test
    fun `marcar una nota como fallida`() {
        val saved = noteRepository.save(newNote("Escaneo borroso").apply { processingStatus = ProcessingStatus.PROCESSING })
        flushAndClear()

        val loaded = noteRepository.findById(saved.id!!).orElseThrow()
        loaded.processingStatus = ProcessingStatus.FAILED
        noteRepository.save(loaded)
        flushAndClear()

        assertEquals(listOf(saved.id), noteRepository.findAllByProcessingStatus(ProcessingStatus.FAILED).map { it.id })
        assertTrue(noteRepository.findAllByProcessingStatus(ProcessingStatus.PROCESSING).isEmpty())
    }

    // ---------- DELETE ----------

    @Test
    fun `eliminar una nota no borra al usuario ni al curso`() {
        val saved = noteRepository.save(newNote("Para borrar"))
        flushAndClear()

        noteRepository.deleteById(saved.id!!)
        flushAndClear()

        assertFalse(noteRepository.existsById(saved.id!!))
        assertTrue(userRepository.existsById(user.id!!))
        assertTrue(courseRepository.existsById(backend.id!!))
    }
}
