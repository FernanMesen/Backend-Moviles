package ApunteIA.BackEnd.controller

import ApunteIA.BackEnd.dto.auth.LoginRequest
import ApunteIA.BackEnd.dto.auth.LoginResponse
import ApunteIA.BackEnd.dto.auth.RegisterUserRequest
import ApunteIA.BackEnd.dto.auth.ChangePasswordRequest
import ApunteIA.BackEnd.dto.auth.DirectResetPasswordRequest
import ApunteIA.BackEnd.dto.auth.ForgotPasswordRequest
import ApunteIA.BackEnd.dto.auth.ResetPasswordRequest
import ApunteIA.BackEnd.dto.auth.MessageResponse
import ApunteIA.BackEnd.dto.user.UserResponse
import ApunteIA.BackEnd.service.AuthService
import ApunteIA.BackEnd.security.AuthenticatedUser
import jakarta.validation.Valid
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.servlet.support.ServletUriComponentsBuilder

@RestController
@RequestMapping("/auth", produces = [MediaType.APPLICATION_JSON_VALUE])
class AuthController(private val authService: AuthService) {

    /** POST /auth/login → 200 | 400 | 401 | 403 */
    @PostMapping("/login", consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun login(@Valid @RequestBody request: LoginRequest): ResponseEntity<LoginResponse> =
        ResponseEntity.ok(authService.login(request))

    /** POST /auth/register → 201 + Location: /users/{id} | 400 | 409 */
    @PostMapping("/register", consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun register(@Valid @RequestBody request: RegisterUserRequest): ResponseEntity<UserResponse> {
        val created = authService.register(request)
        val location = ServletUriComponentsBuilder.fromCurrentContextPath()
            .path("/users/{id}")
            .buildAndExpand(created.id)
            .toUri()
        return ResponseEntity.created(location).body(created)
    }

    /** PUT /auth/password â†’ 204 | 400. Requiere JWT; el ID siempre sale del token validado. */
    @PutMapping("/password", consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun changePassword(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @Valid @RequestBody request: ChangePasswordRequest,
    ): ResponseEntity<Void> {
        authService.changePassword(user.id, request)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/forgot-password", consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun forgotPassword(@Valid @RequestBody request: ForgotPasswordRequest): ResponseEntity<MessageResponse> {
        authService.requestPasswordReset(request)
        return ResponseEntity.ok(MessageResponse("Si la cuenta es elegible, podrás continuar con la recuperación."))
    }

    @PostMapping("/reset-password", consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun resetPassword(@Valid @RequestBody request: ResetPasswordRequest): ResponseEntity<Void> {
        authService.resetPassword(request)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/direct-reset-password", consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun directResetPassword(@Valid @RequestBody request: DirectResetPasswordRequest): ResponseEntity<MessageResponse> {
        authService.directResetPassword(request)
        return ResponseEntity.ok(MessageResponse("Contraseña actualizada correctamente"))
    }
}
