package ApunteIA.BackEnd.service

import ApunteIA.BackEnd.dto.attempt.AttemptResponse
import ApunteIA.BackEnd.dto.attempt.StartAttemptRequest
import ApunteIA.BackEnd.dto.attempt.SubmitAttemptRequest

interface AttemptService {
    fun findById(id: Long): AttemptResponse
    fun findByUser(userId: Long): List<AttemptResponse>
    fun start(assessmentId: Long, request: StartAttemptRequest): AttemptResponse
    fun submit(attemptId: Long, request: SubmitAttemptRequest): AttemptResponse
}