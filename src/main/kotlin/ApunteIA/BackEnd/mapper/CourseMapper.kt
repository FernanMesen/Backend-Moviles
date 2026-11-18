package ApunteIA.BackEnd.mapper

import ApunteIA.BackEnd.dto.course.CourseResponse
import ApunteIA.BackEnd.dto.course.CreateCourseRequest
import ApunteIA.BackEnd.entity.Course
import ApunteIA.BackEnd.entity.User
import org.springframework.stereotype.Component

@Component
class CourseMapper {
    fun toResponse(course: Course, studentCount: Int = 0) = CourseResponse(
        id = requireNotNull(course.id) { "El curso no ha sido guardado" },
        code = course.code,
        name = course.name,
        description = course.description,
        syllabus = course.syllabus,
        createdById = course.createdBy?.id,
        createdByName = course.createdBy?.fullName,
        studentCount = studentCount,
    )

    fun toEntity(request: CreateCourseRequest, creator: User? = null) = Course(
        code = request.code.trim().uppercase(java.util.Locale.ROOT),
        name = request.name.trim(),
        description = request.description?.trim(),
        syllabus = request.syllabus?.trim()?.ifBlank { null },
        createdBy = creator,
    )
}
