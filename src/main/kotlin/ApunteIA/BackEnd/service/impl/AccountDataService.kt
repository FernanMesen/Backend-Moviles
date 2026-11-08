package ApunteIA.BackEnd.service.impl

import ApunteIA.BackEnd.exception.NotFoundException
import ApunteIA.BackEnd.entity.DeletedAccountTombstone
import ApunteIA.BackEnd.repository.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AccountDataService(
    private val userRepository: UserRepository,
    private val noteRepository: NoteRepository,
    private val enrollmentRepository: EnrollmentRepository,
    private val attemptRepository: AssessmentAttemptRepository,
    private val assessmentRepository: AssessmentRepository,
    private val securityLogRepository: SecurityLogRepository,
    private val resetTokenRepository: PasswordResetTokenRepository,
    private val tombstoneRepository: DeletedAccountTombstoneRepository,
) {
    @Transactional(readOnly = true)
    fun export(userId: Long): Map<String, Any?> {
        val user = userRepository.findById(userId).orElseThrow { NotFoundException("La cuenta ya no existe") }
        return mapOf(
            "exportedAt" to java.time.Instant.now(),
            "account" to mapOf(
                "id" to user.id, "email" to user.email, "fullName" to user.fullName,
                "role" to user.role.name, "privilege" to user.privilege.name,
                "authProvider" to user.authProvider, "status" to user.status,
                "createdAt" to user.createdAt, "updatedAt" to user.updatedAt, "lastLoginAt" to user.lastLoginAt,
            ),
            "notes" to noteRepository.findAllByUser_Id(userId).map { note -> mapOf(
                "id" to note.id, "title" to note.title, "content" to note.content, "summary" to note.summary,
                "courseId" to note.course.id, "courseCode" to note.course.code,
                "processingStatus" to note.processingStatus, "noteDate" to note.noteDate,
                "createdAt" to note.createdAt, "updatedAt" to note.updatedAt,
            ) },
            "enrollments" to enrollmentRepository.findAllByUser_Id(userId).map { enrollment -> mapOf(
                "id" to enrollment.id, "courseId" to enrollment.course.id, "courseCode" to enrollment.course.code,
                "courseName" to enrollment.course.name, "enrolledAt" to enrollment.enrolledAt,
            ) },
            "assessmentAttempts" to attemptRepository.findAllByUser_IdOrderByStartedAtDesc(userId).map { attempt -> mapOf(
                "id" to attempt.id, "assessmentId" to attempt.assessment.id,
                "assessmentTitle" to attempt.assessment.title, "score" to attempt.score,
                "startedAt" to attempt.startedAt, "completedAt" to attempt.completedAt,
                "durationSeconds" to attempt.durationSeconds, "answers" to attempt.userAnswers,
            ) },
            "createdAssessments" to assessmentRepository.findAllByCreatedBy_Id(userId).map { assessment -> mapOf(
                "id" to assessment.id, "title" to assessment.title, "description" to assessment.description,
                "type" to assessment.type, "difficulty" to assessment.difficulty,
                "courseId" to assessment.course?.id, "questions" to assessment.questions,
            ) },
            "securityEvents" to securityLogRepository.findAllByUser_IdOrderByOccurredAtDesc(userId).map { log -> mapOf(
                "id" to log.id, "eventType" to log.eventType, "details" to log.details,
                "occurredAt" to log.occurredAt,
            ) },
        )
    }

    @Transactional
    fun deletePermanently(userId: Long) {
        val user = userRepository.findById(userId).orElseThrow { NotFoundException("La cuenta ya no existe") }
        val hash = DeletedAccountTombstone.emailHash(user.email)
        if (!tombstoneRepository.existsByEmailHash(hash)) {
            tombstoneRepository.save(DeletedAccountTombstone(hash))
        }
        resetTokenRepository.deleteAllByUser_Id(userId)
        attemptRepository.deleteAllByUser_Id(userId)
        noteRepository.deleteAllByUser_Id(userId)
        enrollmentRepository.deleteAllByUser_Id(userId)
        assessmentRepository.clearCreator(userId)
        securityLogRepository.anonymizeUser(userId)
        userRepository.delete(user)
        userRepository.flush()
    }
}
