package ApunteIA.BackEnd.controller

import ApunteIA.BackEnd.dto.assessment.AssessmentResponse
import ApunteIA.BackEnd.dto.assessment.GenerateAssessmentRequest
import ApunteIA.BackEnd.security.AuthenticatedUser
import ApunteIA.BackEnd.service.AssessmentService
import jakarta.validation.Valid
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.support.ServletUriComponentsBuilder

@RestController
@RequestMapping("/notes", produces = [MediaType.APPLICATION_JSON_VALUE])
class NoteAssessmentController(private val assessmentService: AssessmentService) {

    @PostMapping("/{id}/assessments/generate", consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun generate(
        @PathVariable id: Long,
        @AuthenticationPrincipal user: AuthenticatedUser,
        @Valid @RequestBody request: GenerateAssessmentRequest,
    ): ResponseEntity<AssessmentResponse> {
        val created = assessmentService.generateFromNote(id, user.id, request)
        val location = ServletUriComponentsBuilder.fromCurrentContextPath()
            .path("/assessments/{id}")
            .buildAndExpand(created.id)
            .toUri()
        return ResponseEntity.created(location).body(created)
    }
}