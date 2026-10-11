package ApunteIA.BackEnd.controller

import ApunteIA.BackEnd.dto.auth.LoginRequest
import ApunteIA.BackEnd.dto.auth.LoginResponse
import ApunteIA.BackEnd.dto.auth.RegisterUserRequest
import ApunteIA.BackEnd.dto.user.UserResponse
import ApunteIA.BackEnd.service.AuthService
import jakarta.validation.Valid
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
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
}