package ApunteIA.BackEnd.service.impl

import ApunteIA.BackEnd.dto.course.CourseResponse
import ApunteIA.BackEnd.dto.course.CreateCourseRequest
import ApunteIA.BackEnd.dto.course.UpdateCourseRequest
import ApunteIA.BackEnd.exception.ConflictException
import ApunteIA.BackEnd.exception.NotFoundException
import ApunteIA.BackEnd.mapper.CourseMapper
import ApunteIA.BackEnd.repository.CourseRepository
import ApunteIA.BackEnd.service.CourseService
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class CourseServiceImpl(
    private val courseRepository: CourseRepository,
    private val courseMapper: CourseMapper,
) : CourseService {

    override fun findAll(): List<CourseResponse> =
        courseRepository.findAll(Sort.by(Sort.Direction.ASC, "id")).map(courseMapper::toResponse)

    override fun findById(id: Long): CourseResponse = courseMapper.toResponse(findCourseOrThrow(id))

    @Transactional
    override fun create(request: CreateCourseRequest): CourseResponse {
        val code = request.code.trim()
        if (courseRepository.existsByCode(code)) {
            throw ConflictException("Ya existe un curso con el código $code")
        }
        val course = courseMapper.toEntity(request)
        return courseMapper.toResponse(courseRepository.saveAndFlush(course))
    }

    @Transactional
    override fun update(id: Long, request: UpdateCourseRequest): CourseResponse {
        val course = findCourseOrThrow(id)
        val code = request.code.trim()
        if (code != course.code && courseRepository.existsByCode(code)) {
            throw ConflictException("Ya existe un curso con el código $code")
        }
        course.code = code
        course.name = request.name.trim()
        course.description = request.description?.trim()
        return courseMapper.toResponse(courseRepository.saveAndFlush(course))
    }

    @Transactional
    override fun delete(id: Long) {
        courseRepository.delete(findCourseOrThrow(id))
        courseRepository.flush()
    }

    private fun findCourseOrThrow(id: Long) = courseRepository.findById(id)
        .orElseThrow { NotFoundException("No existe un curso con id $id") }
}
