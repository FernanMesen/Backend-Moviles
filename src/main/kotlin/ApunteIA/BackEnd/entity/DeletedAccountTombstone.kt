package ApunteIA.BackEnd.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import java.time.Instant

/** Huella irreversible usada para impedir que los seeders vuelvan a crear una cuenta eliminada. */
@Entity
@Table(name = "deleted_account_tombstones")
class DeletedAccountTombstone(
    @Column(name = "email_hash", nullable = false, unique = true, length = 64)
    var emailHash: String,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
) {
    @CreationTimestamp
    @Column(name = "deleted_at", nullable = false, updatable = false)
    var deletedAt: Instant? = null
}
