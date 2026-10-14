package ApunteIA.BackEnd.controller

import ApunteIA.BackEnd.dto.attempt.AttemptResponse
import ApunteIA.BackEnd.dto.attempt.SubmitAttemptRequest
import ApunteIA.BackEnd.service.AttemptService
import jakarta.validation.Valid
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/attempts", produces = [MediaType.APPLICATION_JSON_VALUE])
class AttemptController(private val attemptService: AttemptService) {

    /** GET /attempts/{id} → 200 | 400 | 404 */
    @GetMapping("/{id}")
    fun findById(@PathVariable id: Long): ResponseEntity<AttemptResponse> =
        ResponseEntity.ok(attemptService.findById(id))

    /** PATCH /attempts/{id}/submit → 200 | 400 | 404 | 409 (intento ya completado) */
    @PatchMapping("/{id}/submit", consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun submit(
        @PathVariable id: Long,
        @Valid @RequestBody request: SubmitAttemptRequest,
    ): ResponseEntity<AttemptResponse> = ResponseEntity.ok(attemptService.submit(id, request))
}