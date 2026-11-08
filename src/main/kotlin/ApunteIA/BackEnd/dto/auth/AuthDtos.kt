package ApunteIA.BackEnd.dto.auth

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

// Valores por defecto "" → si falta un campo, responde @NotBlank con un 400 claro.
// @field: es necesario en Kotlin para que la anotación quede en el campo.

/** Entrada de POST /auth/login. Mismo formato que LoginRequestDto del FrontEnd. */
data class LoginRequest(
    @field:NotBlank(message = "El email es obligatorio")
    @field:Email(message = "El email no tiene un formato válido")
    val email: String = "",

    @field:NotBlank(message = "La contraseña es obligatoria")
    val password: String = "",
)

/**
 * Salida de POST /auth/login. Compatible con LoginResponseDto del FrontEnd ({ token, userName }).
 * `token` es un JWT: el cliente lo manda en cada petición como `Authorization: Bearer <token>`.
 */
data class LoginResponse(
    val token: String,
    val tokenType: String = "Bearer",
    /** Segundos de vida del token. */
    val expiresIn: Long,
    val userName: String,
    val userId: Long,
    val email: String,
    val role: String,
)

/** Entrada de POST /auth/register. El rol y el privilegio no los elige el cliente: siempre STUDENT/BASIC. */
data class RegisterUserRequest(
    @field:NotBlank(message = "El nombre completo es obligatorio")
    @field:Size(max = 150, message = "El nombre no puede tener más de 150 caracteres")
    val fullName: String = "",

    @field:NotBlank(message = "El email es obligatorio")
    @field:Email(message = "El email no tiene un formato válido")
    @field:Size(max = 150, message = "El email no puede tener más de 150 caracteres")
    val email: String = "",

    // BCrypt solo usa los primeros 72 bytes, por eso el máximo
    @field:NotBlank(message = "La contraseña es obligatoria")
    @field:Size(min = 6, max = 72, message = "La contraseña debe tener entre 6 y 72 caracteres")
    val password: String = "",
)

/** Entrada de PUT /auth/password para cambiar la clave de la cuenta autenticada. */
data class ChangePasswordRequest(
    @field:NotBlank(message = "La contraseña actual es obligatoria")
    val currentPassword: String = "",

    @field:NotBlank(message = "La nueva contraseña es obligatoria")
    @field:Size(min = 6, max = 72, message = "La nueva contraseña debe tener entre 6 y 72 caracteres")
    val newPassword: String = "",
)

data class ForgotPasswordRequest(
    @field:NotBlank(message = "El email es obligatorio")
    @field:Email(message = "El email no tiene un formato válido")
    val email: String = "",
)

data class ResetPasswordRequest(
    @field:NotBlank(message = "El token es obligatorio")
    val token: String = "",
    @field:NotBlank(message = "La nueva contraseña es obligatoria")
    @field:Size(min = 6, max = 72, message = "La nueva contraseña debe tener entre 6 y 72 caracteres")
    val newPassword: String = "",
)

data class DirectResetPasswordRequest(
    @field:NotBlank(message = "El email es obligatorio")
    @field:Email(message = "El email no tiene un formato válido")
    val email: String = "",

    @field:NotBlank(message = "La nueva contraseña es obligatoria")
    @field:Size(min = 6, max = 72, message = "La nueva contraseña debe tener entre 6 y 72 caracteres")
    val newPassword: String = "",
)

data class MessageResponse(val message: String)
