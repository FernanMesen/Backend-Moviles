package ApunteIA.BackEnd.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

/** Tabla `assessments`, evaluación de un curso con sus preguntas almacenadas como JSON. */
@Entity
@Table(name = "assessments")
class Assessment(
    @Column(name = "title", nullable = false, unique = true, length = 200)
    var title: String,

    @Column(name = "description", length = 1000)
    var description: String? = null,

    @Column(name = "duration_minutes", nullable = false)
    var durationMinutes: Int = 0,

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    var type: AssessmentType = AssessmentType.QUIZ,

    @Enumerated(EnumType.STRING)
    @Column(name = "difficulty", nullable = false, length = 20)
    var difficulty: AssessmentDifficulty = AssessmentDifficulty.MEDIUM,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id")
    var course: Course? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    var createdBy: User? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "questions", nullable = false)
    var questions: List<QuestionData> = emptyList(),

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
) {
    fun grade(selected: List<Int?>): List<UserAnswerData> {
        require(selected.size == questions.size) {
            "Se esperaban ${questions.size} respuestas y se recibieron ${selected.size}"
        }
        return questions.mapIndexed { index, question ->
            UserAnswerData(
                questionIndex = index,
                selectedOptionIndex = selected[index],
                correct = selected[index] == question.correctOptionIndex,
            )
        }
    }

    override fun toString() = "Assessment(id=$id, title=$title)"
}