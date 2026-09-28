package ApunteIA.BackEnd.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

/** Tabla `roles` — ejemplos: STUDENT, ADMIN. */
@Entity
@Table(name = "roles")
class Role(
    @Column(name = "name", nullable = false, unique = true, length = 50)
    var name: String,

    @Column(name = "description", length = 255)
    var description: String? = null,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
) {
    override fun toString() = "Role(id=$id, name=$name)"

    companion object {
        const val STUDENT = "STUDENT"
        const val ADMIN = "ADMIN"
    }
}