package ApunteIA.BackEnd.controller

import ApunteIA.BackEnd.dto.note.CreateNoteRequest
import ApunteIA.BackEnd.dto.note.NoteResponse
import ApunteIA.BackEnd.dto.note.UpdateNoteRequest
import ApunteIA.BackEnd.dto.note.UpdateNoteStatusRequest
import ApunteIA.BackEnd.entity.ProcessingStatus
import ApunteIA.BackEnd.service.NoteService
import jakarta.validation.Valid
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.support.ServletUriComponentsBuilder

@RestController
@RequestMapping("/notes", produces = [MediaType.APPLICATION_JSON_VALUE])
class NoteController(private val noteService: NoteService) {

    /** GET /notes?userId=1&courseId=2&status=PROCESSING → 200 | 400 */
    @GetMapping
    fun findAll(
        @RequestParam(required = false) userId: Long?,
        @RequestParam(required = false) courseId: Long?,
        @RequestParam(required = false) status: ProcessingStatus?,
    ): ResponseEntity<List<NoteResponse>> = ResponseEntity.ok(noteService.findAll(userId, courseId, status))

    /** GET /notes/{id} → 200 | 400 | 404 */
    @GetMapping("/{id}")
    fun findById(@PathVariable id: Long): ResponseEntity<NoteResponse> =
        ResponseEntity.ok(noteService.findById(id))

    /** POST /notes → 201 + Location: /notes/{id} | 400 | 404 */
    @PostMapping(consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun create(@Valid @RequestBody request: CreateNoteRequest): ResponseEntity<NoteResponse> {
        val created = noteService.create(request)
        val location = ServletUriComponentsBuilder.fromCurrentContextPath()
            .path("/notes/{id}")
            .buildAndExpand(created.id)
            .toUri()
        return ResponseEntity.created(location).body(created)
    }

    /** PUT /notes/{id} → 200 | 400 | 404 */
    @PutMapping("/{id}", consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun update(
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdateNoteRequest,
    ): ResponseEntity<NoteResponse> = ResponseEntity.ok(noteService.update(id, request))

    /** PATCH /notes/{id}/status → 200 | 400 | 404 | 409 */
    @PatchMapping("/{id}/status", consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun updateStatus(
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdateNoteStatusRequest,
    ): ResponseEntity<NoteResponse> = ResponseEntity.ok(noteService.updateStatus(id, request.status!!))

    /** POST /notes/{id}/process â†’ 200 | 404 | 409 | 502 */
    @PostMapping("/{id}/process")
    fun processWithAi(@PathVariable id: Long): ResponseEntity<NoteResponse> =
        ResponseEntity.ok(noteService.processWithAi(id))

    /** DELETE /notes/{id} → 204 | 404 */
    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: Long): ResponseEntity<Void> {
        noteService.delete(id)
        return ResponseEntity.noContent().build()
    }
}
