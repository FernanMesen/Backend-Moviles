package ApunteIA.BackEnd.repository

import ApunteIA.BackEnd.entity.AuthProvider
import ApunteIA.BackEnd.entity.User
import ApunteIA.BackEnd.entity.UserStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface UserRepository : JpaRepository<User, Long> {
    /** Login LOCAL. */
    fun findByEmail(email: String): User?
    fun existsByEmail(email: String): Boolean

    /** Login con Google (auth_provider + provider_user_id). */
    fun findByAuthProviderAndProviderUserId(authProvider: AuthProvider, providerUserId: String): User?

    fun findAllByStatus(status: UserStatus): List<User>

    /** Navega la relación: users.role_id → roles.name */
    fun findAllByRole_Name(roleName: String): List<User>

    /** Navega la relación: users.privilege_id → privileges.name */
    fun findAllByPrivilege_Name(privilegeName: String): List<User>
}