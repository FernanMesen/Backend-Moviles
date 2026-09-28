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
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.CreationTimestamp
import java.time.LocalDateTime

/** Entidad asociativa que resuelve la relación N:M entre usuarios y cursos. */
@Entity
@Table(
    name = "enrollments",
    uniqueConstraints = [UniqueConstraint(name = "uk_enrollment_user_course", columnNames = ["user_id", "course_id"])],
)
class Enrollment(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    var course: Course,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
) {
    @CreationTimestamp
    @Column(name = "enrolled_at", nullable = false, updatable = false)
    var enrolledAt: LocalDateTime? = null


    override fun toString() = "Enrollment(id=$id, userId=${user.id}, courseId=${course.id})"
}
