package ApunteIA.BackEnd.controller

import ApunteIA.BackEnd.dto.assessment.AssessmentResponse
import ApunteIA.BackEnd.dto.attempt.AttemptResponse
import ApunteIA.BackEnd.dto.attempt.StartAttemptRequest
import ApunteIA.BackEnd.service.AssessmentService
import ApunteIA.BackEnd.service.AttemptService
import jakarta.validation.Valid
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.support.ServletUriComponentsBuilder

@RestController
@RequestMapping("/assessments", produces = [MediaType.APPLICATION_JSON_VALUE])
class AssessmentController(
    private val assessmentService: AssessmentService,
    private val attemptService: AttemptService,
) {

    /** GET /assessments/{id} → 200 | 400 | 404 */
    @GetMapping("/{id}")
    fun findById(@PathVariable id: Long): ResponseEntity<AssessmentResponse> =
        ResponseEntity.ok(assessmentService.findById(id))

    /** POST /assessments/{id}/attempts → 201 + Location: /attempts/{id} | 400 | 404 | 409 */
    @PostMapping("/{id}/attempts", consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun startAttempt(
        @PathVariable id: Long,
        @Valid @RequestBody request: StartAttemptRequest,
    ): ResponseEntity<AttemptResponse> {
        val created = attemptService.start(id, request)
        val location = ServletUriComponentsBuilder.fromCurrentContextPath()
            .path("/attempts/{id}")
            .buildAndExpand(created.id)
            .toUri()
        return ResponseEntity.created(location).body(created)
    }
}