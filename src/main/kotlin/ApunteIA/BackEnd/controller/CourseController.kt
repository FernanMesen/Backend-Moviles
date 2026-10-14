package ApunteIA.BackEnd.controller

import ApunteIA.BackEnd.dto.course.CourseResponse
import ApunteIA.BackEnd.dto.course.CreateCourseRequest
import ApunteIA.BackEnd.dto.course.UpdateCourseRequest
import ApunteIA.BackEnd.service.CourseService
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
class CourseController(private val courseService: CourseService) {

    /** GET /courses → 200 */
    @GetMapping
    fun findAll(): ResponseEntity<List<CourseResponse>> = ResponseEntity.ok(courseService.findAll())

    /** GET /courses/{id} → 200 | 400 | 404 */
    @GetMapping("/{id}")
    fun findById(@PathVariable id: Long): ResponseEntity<CourseResponse> =
        ResponseEntity.ok(courseService.findById(id))

    /** POST /courses → 201 + Location: /courses/{id} | 400 | 409 */
    @PostMapping(consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun create(@Valid @RequestBody request: CreateCourseRequest): ResponseEntity<CourseResponse> {
        val created = courseService.create(request)
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
    ): ResponseEntity<CourseResponse> = ResponseEntity.ok(courseService.update(id, request))

    /** DELETE /courses/{id} → 204 | 404 | 409 */
    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: Long): ResponseEntity<Void> {
        courseService.delete(id)
        return ResponseEntity.noContent().build()
    }
}
