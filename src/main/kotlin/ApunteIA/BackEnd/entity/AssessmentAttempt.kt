package ApunteIA.BackEnd.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant

/** Tabla `assessment_attempts`, intento de un usuario con sus respuestas almacenadas como JSON. */
@Entity
@Table(name = "assessment_attempts")
class AssessmentAttempt(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assessment_id", nullable = false)
    var assessment: Assessment,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User,

    @Column(name = "score", precision = 5, scale = 2)
    var score: BigDecimal? = null,

    @Column(name = "started_at", nullable = false)
    var startedAt: Instant = Instant.now(),

    @Column(name = "completed_at")
    var completedAt: Instant? = null,

    @Column(name = "duration_seconds", nullable = false)
    var durationSeconds: Long = 0L,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "user_answers", nullable = false)
    var userAnswers: List<UserAnswerData> = emptyList(),

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
) {
    val completed: Boolean
        get() = completedAt != null

    override fun toString() = "AssessmentAttempt(id=$id, score=$score)"

    companion object {
        fun score(answers: List<UserAnswerData>): BigDecimal =
            if (answers.isEmpty()) {
                BigDecimal.ZERO.setScale(2)
            } else {
                BigDecimal(answers.count { it.correct } * 100)
                    .divide(BigDecimal(answers.size), 2, RoundingMode.HALF_UP)
            }

        fun register(
            assessment: Assessment,
            user: User,
            selected: List<Int?>,
            durationSeconds: Long,
            completedAt: Instant = Instant.now(),
        ): AssessmentAttempt {
            val answers = assessment.grade(selected)
            return AssessmentAttempt(
                assessment = assessment,
                user = user,
                score = score(answers),
                startedAt = completedAt.minusSeconds(durationSeconds),
                completedAt = completedAt,
                durationSeconds = durationSeconds,
                userAnswers = answers,
            )
        }
    }
}