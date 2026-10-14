package ApunteIA.BackEnd.service

import ApunteIA.BackEnd.dto.assessment.AssessmentResponse
import ApunteIA.BackEnd.dto.assessment.CreateAssessmentRequest

interface AssessmentService {
    fun findByCourse(courseId: Long): List<AssessmentResponse>
    fun findById(id: Long): AssessmentResponse
    fun create(courseId: Long, request: CreateAssessmentRequest): AssessmentResponse
}