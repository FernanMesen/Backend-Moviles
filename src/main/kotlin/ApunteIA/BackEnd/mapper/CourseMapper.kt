package ApunteIA.BackEnd.mapper

import ApunteIA.BackEnd.dto.course.CourseResponse
import ApunteIA.BackEnd.dto.course.CreateCourseRequest
import ApunteIA.BackEnd.entity.Course
import org.springframework.stereotype.Component

@Component
class CourseMapper {
    fun toResponse(course: Course) = CourseResponse(
        id = requireNotNull(course.id) { "El curso no ha sido guardado" },
        code = course.code,
        name = course.name,
        description = course.description,
        syllabus = course.syllabus,
    )

    fun toEntity(request: CreateCourseRequest) = Course(
        code = request.code.trim(),
        name = request.name.trim(),
        description = request.description?.trim(),
        syllabus = request.syllabus?.trim()?.ifBlank { null },
    )
}
