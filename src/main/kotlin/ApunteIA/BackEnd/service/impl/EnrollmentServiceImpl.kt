package ApunteIA.BackEnd.service.impl

import ApunteIA.BackEnd.dto.enrollment.CreateEnrollmentRequest
import ApunteIA.BackEnd.dto.enrollment.EnrollmentResponse
import ApunteIA.BackEnd.entity.Enrollment
import ApunteIA.BackEnd.exception.BadRequestException
import ApunteIA.BackEnd.exception.ConflictException
import ApunteIA.BackEnd.exception.NotFoundException
import ApunteIA.BackEnd.mapper.EnrollmentMapper
import ApunteIA.BackEnd.repository.CourseRepository
import ApunteIA.BackEnd.repository.EnrollmentRepository
import ApunteIA.BackEnd.repository.UserRepository
import ApunteIA.BackEnd.service.EnrollmentService
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class EnrollmentServiceImpl(
    private val enrollmentRepository: EnrollmentRepository,
    private val userRepository: UserRepository,
    private val courseRepository: CourseRepository,
    private val enrollmentMapper: EnrollmentMapper,
) : EnrollmentService {

    override fun findAll(userId: Long?, courseId: Long?): List<EnrollmentResponse> {
        val enrollments = when {
            userId != null -> enrollmentRepository.findAllByUser_Id(userId)
            courseId != null -> enrollmentRepository.findAllByCourse_Id(courseId)
            else -> enrollmentRepository.findAll(Sort.by(Sort.Direction.ASC, "id"))
        }
        return enrollments
            .filter { courseId == null || it.course.id == courseId }
            .sortedBy { it.id }
            .map(enrollmentMapper::toResponse)
    }

    override fun findById(id: Long): EnrollmentResponse = enrollmentMapper.toResponse(findEnrollmentOrThrow(id))

    @Transactional
    override fun create(request: CreateEnrollmentRequest): EnrollmentResponse {
        val userId = request.userId ?: throw BadRequestException("El usuario es obligatorio")
        val courseId = request.courseId ?: throw BadRequestException("El curso es obligatorio")
        if (userId <= 0) throw BadRequestException("El id del usuario debe ser positivo")
        if (courseId <= 0) throw BadRequestException("El id del curso debe ser positivo")

        val user = userRepository.findById(userId)
            .orElseThrow { NotFoundException("No existe un usuario con id $userId") }
        val course = courseRepository.findById(courseId)
            .orElseThrow { NotFoundException("No existe un curso con id $courseId") }
        if (enrollmentRepository.existsByUser_IdAndCourse_Id(userId, courseId)) {
            throw ConflictException("El usuario $userId ya está inscrito en el curso $courseId")
        }
        val enrollment = Enrollment(user = user, course = course)
        return enrollmentMapper.toResponse(enrollmentRepository.saveAndFlush(enrollment))
    }

    @Transactional
    override fun delete(id: Long) {
        enrollmentRepository.delete(findEnrollmentOrThrow(id))
        enrollmentRepository.flush()
    }

    private fun findEnrollmentOrThrow(id: Long) = enrollmentRepository.findById(id)
        .orElseThrow { NotFoundException("No existe una inscripción con id $id") }
}
