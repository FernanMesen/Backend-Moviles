package ApunteIA.BackEnd.controller

import ApunteIA.BackEnd.dto.enrollment.CreateEnrollmentRequest
import ApunteIA.BackEnd.dto.enrollment.EnrollmentResponse
import ApunteIA.BackEnd.service.EnrollmentService
import jakarta.validation.Valid
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.support.ServletUriComponentsBuilder

@RestController
@RequestMapping("/enrollments", produces = [MediaType.APPLICATION_JSON_VALUE])
class EnrollmentController(private val enrollmentService: EnrollmentService) {

    /** GET /enrollments?userId=1&courseId=2 → 200 | 400 */
    @GetMapping
    fun findAll(
        @RequestParam(required = false) userId: Long?,
        @RequestParam(required = false) courseId: Long?,
    ): ResponseEntity<List<EnrollmentResponse>> = ResponseEntity.ok(enrollmentService.findAll(userId, courseId))

    /** GET /enrollments/{id} → 200 | 400 | 404 */
    @GetMapping("/{id}")
    fun findById(@PathVariable id: Long): ResponseEntity<EnrollmentResponse> =
        ResponseEntity.ok(enrollmentService.findById(id))

    /** POST /enrollments → 201 + Location: /enrollments/{id} | 400 | 404 | 409 */
    @PostMapping(consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun create(@Valid @RequestBody request: CreateEnrollmentRequest): ResponseEntity<EnrollmentResponse> {
        val created = enrollmentService.create(request)
        val location = ServletUriComponentsBuilder.fromCurrentContextPath()
            .path("/enrollments/{id}")
            .buildAndExpand(created.id)
            .toUri()
        return ResponseEntity.created(location).body(created)
    }

    /** DELETE /enrollments/{id} → 204 | 404 */
    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: Long): ResponseEntity<Void> {
        enrollmentService.delete(id)
        return ResponseEntity.noContent().build()
    }
}
