package ApunteIA.BackEnd.service

import ApunteIA.BackEnd.dto.assessment.AssessmentResponse
import ApunteIA.BackEnd.dto.assessment.CreateAssessmentRequest
import ApunteIA.BackEnd.dto.assessment.GenerateAssessmentRequest

interface AssessmentService {
    fun findByCourse(courseId: Long): List<AssessmentResponse>
    fun findById(id: Long): AssessmentResponse
    fun create(courseId: Long, request: CreateAssessmentRequest): AssessmentResponse
    fun generateFromNote(noteId: Long, creatorId: Long, request: GenerateAssessmentRequest): AssessmentResponse
}