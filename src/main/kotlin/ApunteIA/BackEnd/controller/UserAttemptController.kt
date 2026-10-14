package ApunteIA.BackEnd.controller

import ApunteIA.BackEnd.dto.attempt.AttemptResponse
import ApunteIA.BackEnd.service.AttemptService
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/users", produces = [MediaType.APPLICATION_JSON_VALUE])
class UserAttemptController(private val attemptService: AttemptService) {

    /** GET /users/{id}/attempts → 200 | 400 | 404 */
    @GetMapping("/{id}/attempts")
    fun findAll(@PathVariable id: Long): ResponseEntity<List<AttemptResponse>> =
        ResponseEntity.ok(attemptService.findByUser(id))
}