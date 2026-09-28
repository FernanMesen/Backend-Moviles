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
import org.hibernate.annotations.CreationTimestamp
import java.time.LocalDateTime

/** Registro de una acción relacionada con seguridad. El usuario puede faltar para eventos anónimos. */
@Entity
@Table(name = "security_logs")
class SecurityLog(
    @Column(name = "event_type", nullable = false, length = 50)
    var eventType: String,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    var user: User? = null,

    @Column(name = "details", length = 1000)
    var details: String? = null,

    @Column(name = "ip_address", length = 45)
    var ipAddress: String? = null,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
) {
    @CreationTimestamp
    @Column(name = "occurred_at", nullable = false, updatable = false)
    var occurredAt: LocalDateTime? = null

    override fun toString() = "SecurityLog(id=$id, eventType=$eventType, userId=${user?.id})"
}
