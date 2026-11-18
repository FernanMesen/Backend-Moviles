package ApunteIA.BackEnd.controller

import ApunteIA.BackEnd.dto.course.CourseResponse
import ApunteIA.BackEnd.dto.course.CreateCourseRequest
import ApunteIA.BackEnd.dto.course.UpdateCourseRequest
import ApunteIA.BackEnd.dto.enrollment.CreateEnrollmentRequest
import ApunteIA.BackEnd.dto.enrollment.EnrollUserRequest
import ApunteIA.BackEnd.dto.enrollment.EnrollmentResponse
import ApunteIA.BackEnd.service.CourseService
import ApunteIA.BackEnd.service.EnrollmentService
import ApunteIA.BackEnd.security.AuthenticatedUser
import org.springframework.security.core.annotation.AuthenticationPrincipal
import jakarta.validation.Valid
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.support.ServletUriComponentsBuilder

@RestController
@RequestMapping("/courses", produces = [MediaType.APPLICATION_JSON_VALUE])
class CourseController(
    private val courseService: CourseService,
    private val enrollmentService: EnrollmentService,
) {

    /** POST /courses/{id}/enrollments → 201 + Location: /enrollments/{id} | 400 | 404 | 409 */
    @PostMapping("/{id}/enrollments", consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun enroll(
        @PathVariable id: Long,
        @Valid @RequestBody request: EnrollUserRequest,
        @AuthenticationPrincipal requester: AuthenticatedUser?,
    ): ResponseEntity<EnrollmentResponse> {
        val created = if (requester != null) enrollmentService.create(CreateEnrollmentRequest(userId = request.userId, courseId = id), requester) else enrollmentService.create(CreateEnrollmentRequest(userId = request.userId, courseId = id))
        val location = ServletUriComponentsBuilder.fromCurrentContextPath()
            .path("/enrollments/{id}")
            .buildAndExpand(created.id)
            .toUri()
        return ResponseEntity.created(location).body(created)
    }

    /** GET /courses → 200 */
    @GetMapping
    fun findAll(): ResponseEntity<List<CourseResponse>> = ResponseEntity.ok(courseService.findAll())

    /** GET /courses/{id} → 200 | 400 | 404 */
    @GetMapping("/{id}")
    fun findById(@PathVariable id: Long): ResponseEntity<CourseResponse> =
        ResponseEntity.ok(courseService.findById(id))

    /** POST /courses → 201 + Location: /courses/{id} | 400 | 409 */
    @PostMapping(consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun create(
        @Valid @RequestBody request: CreateCourseRequest,
        @AuthenticationPrincipal requester: AuthenticatedUser?,
    ): ResponseEntity<CourseResponse> {
        val created = if (requester != null) courseService.create(request, requester) else courseService.create(request)
        val location = ServletUriComponentsBuilder.fromCurrentContextPath()
            .path("/courses/{id}")
            .buildAndExpand(created.id)
            .toUri()
        return ResponseEntity.created(location).body(created)
    }

    /** PUT /courses/{id} → 200 | 400 | 404 | 409 */
    @PutMapping("/{id}", consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun update(
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdateCourseRequest,
        @AuthenticationPrincipal requester: AuthenticatedUser?,
    ): ResponseEntity<CourseResponse> = if (requester != null) ResponseEntity.ok(courseService.update(id, request, requester)) else ResponseEntity.ok(courseService.update(id, request))

    /** DELETE /courses/{id} → 204 | 404 | 409 */
    @DeleteMapping("/{id}")
    fun delete(
        @PathVariable id: Long,
        @AuthenticationPrincipal requester: AuthenticatedUser?,
    ): ResponseEntity<Void> {
        if (requester != null) courseService.delete(id, requester) else courseService.delete(id)
        return ResponseEntity.noContent().build()
    }
}
