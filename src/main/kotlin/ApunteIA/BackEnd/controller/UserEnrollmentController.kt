package ApunteIA.BackEnd.controller

import ApunteIA.BackEnd.dto.enrollment.EnrollmentResponse
import ApunteIA.BackEnd.service.EnrollmentService
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/users", produces = [MediaType.APPLICATION_JSON_VALUE])
class UserEnrollmentController(private val enrollmentService: EnrollmentService) {

    @GetMapping("/{id}/enrollments")
    fun findAll(@PathVariable id: Long): ResponseEntity<List<EnrollmentResponse>> =
        ResponseEntity.ok(enrollmentService.findAll(userId = id))
}
