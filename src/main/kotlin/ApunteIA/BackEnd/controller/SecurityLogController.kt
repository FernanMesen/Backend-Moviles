package ApunteIA.BackEnd.controller

import ApunteIA.BackEnd.dto.securitylog.SecurityLogResponse
import ApunteIA.BackEnd.service.SecurityLogService
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/security-logs", produces = [MediaType.APPLICATION_JSON_VALUE])
class SecurityLogController(private val securityLogService: SecurityLogService) {

    /** GET /security-logs?userId=1&eventType=LOGIN&anonymousOnly=false → 200 | 400 */
    @GetMapping
    fun findAll(
        @RequestParam(required = false) userId: Long?,
        @RequestParam(required = false) eventType: String?,
        @RequestParam(defaultValue = "false") anonymousOnly: Boolean,
    ): ResponseEntity<List<SecurityLogResponse>> =
        ResponseEntity.ok(securityLogService.findAll(userId, eventType, anonymousOnly))

    /** GET /security-logs/{id} → 200 | 400 | 404 */
    @GetMapping("/{id}")
    fun findById(@PathVariable id: Long): ResponseEntity<SecurityLogResponse> =
        ResponseEntity.ok(securityLogService.findById(id))
}
