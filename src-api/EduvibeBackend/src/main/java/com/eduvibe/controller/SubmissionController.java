package com.eduvibe.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.eduvibe.dto.submission.SubmissionToGradeResponse;
import com.eduvibe.dto.submission.GradeRequest;
import com.eduvibe.dto.submission.SubmissionResponse;
import com.eduvibe.dto.submission.TeacherNoteRequest;
import com.eduvibe.service.SubmissionService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import com.eduvibe.dto.common.PageResponse;

/**
 * Corrección de entregas.
 */
@RestController
@RequestMapping("/api/submissions")
@RequiredArgsConstructor
public class SubmissionController {

    private final SubmissionService submissionService;

    /** Entregas sin corregir de todas las clases del profesorado, para su acceso directo "Calificar". */
    @GetMapping("/pending-review")
    public ResponseEntity<PageResponse<SubmissionToGradeResponse>> toGrade(Pageable pageable) {
        return ResponseEntity.ok(submissionService.toGrade(pageable));
    }

    /**
     * Pone o corrige la nota. Es PUT porque una entrega tiene una sola
     * calificación: volver a llamarlo la sustituye, no añade otra.
     */
    @PutMapping("/{submissionId}/grade")
    public ResponseEntity<SubmissionResponse> grade(@PathVariable UUID submissionId,
                                                        @Valid @RequestBody GradeRequest request) {
        return ResponseEntity.ok(submissionService.grade(submissionId, request));
    }

    /** Nota rápida sobre la entrega, sin calificarla. Igual que la nota, sustituye a la anterior si ya había una. */
    @PutMapping("/{submissionId}/comment")
    public ResponseEntity<SubmissionResponse> comment(@PathVariable UUID submissionId,
                                                       @Valid @RequestBody TeacherNoteRequest request) {
        return ResponseEntity.ok(submissionService.comment(submissionId, request));
    }
}
