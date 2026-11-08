package ApunteIA.BackEnd.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import java.security.MessageDigest
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

    companion object {
        fun emailHash(email: String): String = MessageDigest.getInstance("SHA-256")
            .digest(email.trim().lowercase().toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
