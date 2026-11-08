package ApunteIA.BackEnd.repository

import ApunteIA.BackEnd.entity.PasswordResetToken
import org.springframework.data.jpa.repository.JpaRepository

interface PasswordResetTokenRepository : JpaRepository<PasswordResetToken, Long> {
    fun findByTokenHash(tokenHash: String): PasswordResetToken?
    fun deleteAllByUser_Id(userId: Long)
}
