package ApunteIA.BackEnd.service.impl

import ApunteIA.BackEnd.dto.course.CourseResponse
import ApunteIA.BackEnd.dto.course.CreateCourseRequest
import ApunteIA.BackEnd.dto.course.UpdateCourseRequest
import ApunteIA.BackEnd.entity.Enrollment
import ApunteIA.BackEnd.exception.ConflictException
import ApunteIA.BackEnd.exception.ForbiddenException
import ApunteIA.BackEnd.exception.NotFoundException
import ApunteIA.BackEnd.mapper.CourseMapper
import ApunteIA.BackEnd.repository.AssessmentRepository
import ApunteIA.BackEnd.repository.CourseRepository
import ApunteIA.BackEnd.repository.EnrollmentRepository
import ApunteIA.BackEnd.repository.NoteRepository
import ApunteIA.BackEnd.repository.UserRepository
import ApunteIA.BackEnd.security.AuthenticatedUser
import ApunteIA.BackEnd.service.CourseService
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.Locale

@Service
@Transactional(readOnly = true)
class CourseServiceImpl(
    private val courseRepository: CourseRepository,
    private val enrollmentRepository: EnrollmentRepository,
    private val userRepository: UserRepository,
    private val noteRepository: NoteRepository,
    private val assessmentRepository: AssessmentRepository,
    private val courseMapper: CourseMapper,
) : CourseService {

    override fun findAll(): List<CourseResponse> =
        courseRepository.findAll(Sort.by(Sort.Direction.ASC, "id")).map { course ->
            courseMapper.toResponse(course, enrollmentRepository.countByCourse_Id(course.id!!).toInt())
        }

    override fun findById(id: Long): CourseResponse {
        val course = findCourseOrThrow(id)
        return courseMapper.toResponse(course, enrollmentRepository.countByCourse_Id(id).toInt())
    }

    @Transactional
    override fun create(request: CreateCourseRequest, requester: AuthenticatedUser): CourseResponse {
        val code = normalizeCode(request.code)
        if (courseRepository.findByCodeIgnoreCase(code) != null) {
            throw ConflictException("Ya existe un curso con el código $code")
        }
        val creator = userRepository.findById(requester.id)
            .orElseThrow { NotFoundException("No existe un usuario con id ${requester.id}") }
        val course = courseMapper.toEntity(request.copy(code = code), creator)
        return try {
            val saved = courseRepository.saveAndFlush(course)
            enrollmentRepository.saveAndFlush(Enrollment(user = creator, course = saved))
            courseMapper.toResponse(saved, 1)
        } catch (_: DataIntegrityViolationException) {
            throw ConflictException("Ya existe un curso con el código $code")
        }
    }

    @Transactional
    override fun update(id: Long, request: UpdateCourseRequest, requester: AuthenticatedUser): CourseResponse {
        val course = findCourseOrThrow(id)
        val allowed = requester.isAdmin || course.createdBy?.id == requester.id
        if (!allowed) throw ForbiddenException("Solo el creador del curso o un administrador puede modificarlo")

        val code = normalizeCode(request.code)
        val existing = courseRepository.findByCodeIgnoreCase(code)
        if (existing != null && existing.id != course.id) {
            throw ConflictException("Ya existe un curso con el código $code")
        }
        course.code = code
        course.name = request.name.trim()
        course.description = request.description?.trim()
        request.syllabus?.let { course.syllabus = it.trim().ifBlank { null } }
        return try {
            val saved = courseRepository.saveAndFlush(course)
            courseMapper.toResponse(saved, enrollmentRepository.countByCourse_Id(saved.id!!).toInt())
        } catch (_: DataIntegrityViolationException) {
            throw ConflictException("Ya existe un curso con el código $code")
        }
    }

    @Transactional
    override fun delete(id: Long, requester: AuthenticatedUser) {
        val course = findCourseOrThrow(id)
        val allowed = requester.isAdmin || course.createdBy?.id == requester.id
        if (!allowed) throw ForbiddenException("Solo el creador del curso o un administrador puede modificarlo")

        val enrollments = enrollmentRepository.findAllByCourse_Id(id)
        val hasNotes = noteRepository.findAllByCourse_Id(id).isNotEmpty()
        val hasAssessments = assessmentRepository.findAllByCourse_Id(id).isNotEmpty()
        val onlyCreatorEnrollment = enrollments.size == 1 && enrollments.single().user.id == course.createdBy?.id

        if (enrollments.isNotEmpty() && !onlyCreatorEnrollment) {
            throw ConflictException("No se puede eliminar un curso con estudiantes o contenido asociado")
        }
        if (hasNotes || hasAssessments) {
            throw ConflictException("No se puede eliminar un curso con estudiantes o contenido asociado")
        }
        if (enrollments.size == 1) enrollmentRepository.delete(enrollments.single())
        try {
            courseRepository.delete(course)
            courseRepository.flush()
        } catch (_: DataIntegrityViolationException) {
            throw ConflictException("No se puede eliminar un curso con estudiantes o contenido asociado")
        }
    }

    // Sobrecargas heredadas para preservar compatibilidad con pruebas directas.
    override fun create(request: CreateCourseRequest): CourseResponse {
        val code = normalizeCode(request.code)
        if (courseRepository.findByCodeIgnoreCase(code) != null) throw ConflictException("Ya existe un curso con el código $code")
        val course = courseMapper.toEntity(request.copy(code = code), null)
        return courseMapper.toResponse(courseRepository.saveAndFlush(course), 0)
    }

    override fun update(id: Long, request: UpdateCourseRequest): CourseResponse {
        val course = findCourseOrThrow(id)
        val code = normalizeCode(request.code)
        val existing = courseRepository.findByCodeIgnoreCase(code)
        if (existing != null && existing.id != course.id) throw ConflictException("Ya existe un curso con el código $code")
        course.code = code
        course.name = request.name.trim()
        course.description = request.description?.trim()
        request.syllabus?.let { course.syllabus = it.trim().ifBlank { null } }
        return courseMapper.toResponse(courseRepository.saveAndFlush(course), enrollmentRepository.countByCourse_Id(id).toInt())
    }

    override fun delete(id: Long) {
        val course = findCourseOrThrow(id)
        val enrollments = enrollmentRepository.findAllByCourse_Id(id)
        if (enrollments.isNotEmpty() || noteRepository.findAllByCourse_Id(id).isNotEmpty() || assessmentRepository.findAllByCourse_Id(id).isNotEmpty()) {
            throw DataIntegrityViolationException("Curso relacionado")
        }
        courseRepository.delete(course)
        courseRepository.flush()
    }

    private fun normalizeCode(code: String): String = code.trim().uppercase(Locale.ROOT)

    private fun findCourseOrThrow(id: Long) = courseRepository.findById(id)
        .orElseThrow { NotFoundException("No existe un curso con id $id") }
}
