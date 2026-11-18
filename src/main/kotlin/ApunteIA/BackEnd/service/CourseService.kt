package ApunteIA.BackEnd.service

import ApunteIA.BackEnd.dto.course.CourseResponse
import ApunteIA.BackEnd.dto.course.CreateCourseRequest
import ApunteIA.BackEnd.dto.course.UpdateCourseRequest
import ApunteIA.BackEnd.security.AuthenticatedUser

interface CourseService {
    fun findAll(): List<CourseResponse>
    fun findById(id: Long): CourseResponse
    fun create(request: CreateCourseRequest, requester: AuthenticatedUser): CourseResponse
    fun update(id: Long, request: UpdateCourseRequest, requester: AuthenticatedUser): CourseResponse
    fun delete(id: Long, requester: AuthenticatedUser)

    // Compatibilidad para pruebas y llamadas internas heredadas; las rutas HTTP usan la identidad JWT.
    fun create(request: CreateCourseRequest): CourseResponse
    fun update(id: Long, request: UpdateCourseRequest): CourseResponse
    fun delete(id: Long)
}
