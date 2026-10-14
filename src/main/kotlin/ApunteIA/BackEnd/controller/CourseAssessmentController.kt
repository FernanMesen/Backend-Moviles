package ApunteIA.BackEnd.controller

import ApunteIA.BackEnd.dto.assessment.AssessmentResponse
import ApunteIA.BackEnd.dto.assessment.CreateAssessmentRequest
import ApunteIA.BackEnd.service.AssessmentService
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
@RequestMapping("/courses", produces = [MediaType.APPLICATION_JSON_VALUE])
class CourseAssessmentController(private val assessmentService: AssessmentService) {

    /** GET /courses/{id}/assessments → 200 | 400 | 404 */
    @GetMapping("/{id}/assessments")
    fun findAll(@PathVariable id: Long): ResponseEntity<List<AssessmentResponse>> =
        ResponseEntity.ok(assessmentService.findByCourse(id))

    /** POST /courses/{id}/assessments → 201 + Location: /assessments/{id} | 400 | 404 | 409 */
    @PostMapping("/{id}/assessments", consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun create(
        @PathVariable id: Long,
        @Valid @RequestBody request: CreateAssessmentRequest,
    ): ResponseEntity<AssessmentResponse> {
        val created = assessmentService.create(id, request)
        val location = ServletUriComponentsBuilder.fromCurrentContextPath()
            .path("/assessments/{id}")
            .buildAndExpand(created.id)
            .toUri()
        return ResponseEntity.created(location).body(created)
    }
}