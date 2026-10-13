package com.eduvibe.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.eduvibe.dto.assignment.AssignmentDetailResponse;
import com.eduvibe.dto.assignment.SaveAssignmentRequest;
import com.eduvibe.dto.rubric.RubricResponse;
import com.eduvibe.dto.rubric.SaveRubricRequest;
import com.eduvibe.dto.submission.SubmissionResponse;
import com.eduvibe.dto.submission.SubmitRequest;
import com.eduvibe.service.AssignmentService;
import com.eduvibe.service.RubricService;
import com.eduvibe.service.SubmissionService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Una tarea concreta y lo que se hace con ella.
 *
 * Crear una tarea cuelga de su clase ({@code POST /api/classes/{id}/assignments})
 * porque ahí es donde nace; a partir de ese momento la tarea tiene identidad
 * propia y se maneja por su identificador.
 */
@RestController
@RequestMapping("/api/assignments")
@RequiredArgsConstructor
public class AssignmentController {

    private final AssignmentService assignmentService;
    private final SubmissionService submissionService;
    private final RubricService rubricService;

    @GetMapping("/{assignmentId}")
    public ResponseEntity<AssignmentDetailResponse> detail(@PathVariable UUID assignmentId) {
        return ResponseEntity.ok(assignmentService.detail(assignmentId));
    }

    @PutMapping("/{assignmentId}")
    public ResponseEntity<AssignmentDetailResponse> update(@PathVariable UUID assignmentId,
                                                               @Valid @RequestBody SaveAssignmentRequest request) {
        return ResponseEntity.ok(assignmentService.update(assignmentId, request));
    }

    @DeleteMapping("/{assignmentId}")
    public ResponseEntity<Void> delete(@PathVariable UUID assignmentId) {
        assignmentService.delete(assignmentId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Guarda o envía la entrega de quien está autenticado.
     *
     * Es PUT y no POST porque hay como mucho una entrega por alumno y tarea:
     * llamarlo dos veces deja el mismo resultado que llamarlo una.
     */
    @PutMapping("/{assignmentId}/submission")
    public ResponseEntity<SubmissionResponse> submit(@PathVariable UUID assignmentId,
                                                       @Valid @RequestBody SubmitRequest request) {
        return ResponseEntity.ok(submissionService.saveMySubmission(assignmentId, request));
    }

    /** Todas las entregas de la tarea. Solo para el profesorado de la clase. */
    @GetMapping("/{assignmentId}/submissions")
    public ResponseEntity<List<SubmissionResponse>> submissions(@PathVariable UUID assignmentId) {
        return ResponseEntity.ok(submissionService.listByAssignment(assignmentId));
    }

    /** Crea la rúbrica de la tarea, o sustituye por completo la que ya hubiera. */
    @PutMapping("/{assignmentId}/rubric")
    public ResponseEntity<RubricResponse> saveRubric(@PathVariable UUID assignmentId,
                                                         @Valid @RequestBody SaveRubricRequest request) {
        return ResponseEntity.ok(rubricService.save(assignmentId, request));
    }

    @DeleteMapping("/{assignmentId}/rubric")
    public ResponseEntity<Void> deleteRubric(@PathVariable UUID assignmentId) {
        rubricService.delete(assignmentId);
        return ResponseEntity.noContent().build();
    }
}
