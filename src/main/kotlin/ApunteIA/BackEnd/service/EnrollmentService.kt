package ApunteIA.BackEnd.service

import ApunteIA.BackEnd.dto.enrollment.CreateEnrollmentRequest
import ApunteIA.BackEnd.dto.enrollment.EnrollmentResponse

interface EnrollmentService {
    fun findAll(userId: Long? = null, courseId: Long? = null): List<EnrollmentResponse>
    fun findById(id: Long): EnrollmentResponse
    fun create(request: CreateEnrollmentRequest): EnrollmentResponse
    fun delete(id: Long)
}
