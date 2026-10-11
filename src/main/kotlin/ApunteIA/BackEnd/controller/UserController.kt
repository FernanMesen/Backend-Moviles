package ApunteIA.BackEnd.controller

import ApunteIA.BackEnd.dto.user.UpdateUserStatusRequest
import ApunteIA.BackEnd.dto.user.UserResponse
import ApunteIA.BackEnd.entity.UserStatus
import ApunteIA.BackEnd.service.UserService
import jakarta.validation.Valid
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/users", produces = [MediaType.APPLICATION_JSON_VALUE])
class UserController(private val userService: UserService) {

    /** GET /users y GET /users?status=BLOCKED → 200 | 400 */
    @GetMapping
    fun findAll(@RequestParam(required = false) status: UserStatus?): ResponseEntity<List<UserResponse>> =
        ResponseEntity.ok(userService.findAll(status))

    /** GET /users/{id} → 200 | 400 | 404 */
    @GetMapping("/{id}")
    fun findById(@PathVariable id: Long): ResponseEntity<UserResponse> =
        ResponseEntity.ok(userService.findById(id))

    /** PATCH /users/{id}/status → 200 | 400 | 404 | 409 */
    @PatchMapping("/{id}/status", consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun updateStatus(
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdateUserStatusRequest,
    ): ResponseEntity<UserResponse> =
        ResponseEntity.ok(userService.updateStatus(id, request.status!!)) // @Valid ya garantizó @NotNull
}