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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id")
    var course: Course? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "questions", nullable = false, columnDefinition = "jsonb")
    var questions: List<QuestionData> = emptyList(),

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
) {
    override fun toString() = "Assessment(id=$id, title=$title)"
}