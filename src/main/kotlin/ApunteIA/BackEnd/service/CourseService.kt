package ApunteIA.BackEnd.service

import ApunteIA.BackEnd.dto.course.CourseResponse
import ApunteIA.BackEnd.dto.course.CreateCourseRequest
import ApunteIA.BackEnd.dto.course.UpdateCourseRequest

interface CourseService {
    fun findAll(): List<CourseResponse>
    fun findById(id: Long): CourseResponse
    fun create(request: CreateCourseRequest): CourseResponse
    fun update(id: Long, request: UpdateCourseRequest): CourseResponse
    fun delete(id: Long)
}
