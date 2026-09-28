package ApunteIA.BackEnd.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

/** Tabla `courses`, representa un curso que puede tener varias inscripciones. */
@Entity
@Table(name = "courses")
class Course(
    @Column(name = "code", nullable = false, unique = true, length = 50)
    var code: String,

    @Column(name = "name", nullable = false, length = 150)
    var name: String,

    @Column(name = "description", length = 1000)
    var description: String? = null,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
) {
    override fun toString() = "Course(id=$id, code=$code, name=$name)"
}
