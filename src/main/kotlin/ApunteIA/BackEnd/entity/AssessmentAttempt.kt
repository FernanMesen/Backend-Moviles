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

    @Column(name = "score_percent", nullable = false)
    var scorePercent: Int = 0,

    @Column(name = "completed_at", nullable = false)
    var completedAt: Instant = Instant.now(),

    @Column(name = "duration_seconds", nullable = false)
    var durationSeconds: Long = 0L,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "user_answers", nullable = false, columnDefinition = "jsonb")
    var userAnswers: List<UserAnswerData> = emptyList(),

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
) {
    override fun toString() = "AssessmentAttempt(id=$id, scorePercent=$scorePercent)"
}