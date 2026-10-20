package ApunteIA.BackEnd.security

/**
 * Usuario dueño del token, ya validado por el JwtAuthenticationFilter.
 *
 * Cualquier controlador del equipo lo puede recibir así:
 *   fun crear(@AuthenticationPrincipal user: AuthenticatedUser, ...) { user.id ... }
 */
data class AuthenticatedUser(
    val id: Long,
    val email: String,
    val role: String,
) {
    val isAdmin: Boolean get() = role == ApunteIA.BackEnd.entity.Role.ADMIN
}