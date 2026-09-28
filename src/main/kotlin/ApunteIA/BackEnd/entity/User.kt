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
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.LocalDateTime

/**
 * Tabla `users`.
 *  - role_id      → roles.id       (N usuarios : 1 rol)
 *  - privilege_id → privileges.id  (N usuarios : 1 privilegio)
 *
 * Las demás entidades del equipo (SecurityLog, Course, Enrollment, Note,
 * Assessment, AssessmentAttempt) apuntan a esta clase con su propio @ManyToOne.
 */
@Entity
@Table(name = "users")
class User(
    @Column(name = "email", nullable = false, unique = true, length = 150)
    var email: String,

    @Column(name = "full_name", nullable = false, length = 150)
    var fullName: String,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    var role: Role,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "privilege_id", nullable = false)
    var privilege: Privilege,

    /** Null para cuentas GOOGLE, que no tienen contraseña local. */
    @Column(name = "password_hash", length = 255)
    var passwordHash: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_provider", nullable = false, length = 20)
    var authProvider: AuthProvider = AuthProvider.LOCAL,

    /** ID de Google, solo si authProvider == GOOGLE. */
    @Column(name = "provider_user_id", length = 150)
    var providerUserId: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: UserStatus = UserStatus.ACTIVE,

    @Column(name = "last_login_at")
    var lastLoginAt: LocalDateTime? = null,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
) {
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime? = null

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime? = null

    // No se imprimen role/privilege para no disparar la carga LAZY fuera de una transacción
    override fun toString() = "User(id=$id, email=$email, status=$status)"
}