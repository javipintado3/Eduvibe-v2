package com.eduvibe.dto.submission;

import java.time.Instant;
import java.util.UUID;

import com.eduvibe.model.Submission;

/**
 * Una entrega a la espera de corrección, con lo justo para saber qué es y
 * llevar al profesorado a corregirla, sin traer el contenido entregado.
 *
 * @param groupName el subgrupo que la entregó, en una tarea grupal; en ese caso
 *                  {@code studentName} es solo uno de sus miembros, porque se
 *                  lista una fila por subgrupo y no una por alumno
 */
public record SubmissionToGradeResponse(
        UUID submissionId,
        UUID assignmentId,
        String assignmentTitle,
        UUID classId,
        String className,
        String studentName,
        String groupName,
        Instant submittedAt,
        boolean entregadaTarde) {

    public static SubmissionToGradeResponse of(Submission submission) {
        return new SubmissionToGradeResponse(
                submission.getId(),
                submission.getAssignment().getId(),
                submission.getAssignment().getTitle(),
                submission.getAssignment().getSchoolClass().getId(),
                submission.getAssignment().getSchoolClass().getName(),
                submission.getStudent().getName(),
                submission.getClassGroup() == null ? null : submission.getClassGroup().getName(),
                submission.getSubmittedAt(),
                submission.entregadaTarde());
    }
}
