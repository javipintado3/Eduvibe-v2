package com.eduvibe.dto.submission;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.eduvibe.model.Submission;

/**
 * Una entrega, con su estado y su nota si ya está corregida.
 *
 * @param entregadaTarde se calcula comparando la fecha de envío con el plazo de
 *                       la tarea; no está guardado en ninguna columna, y por
 *                       eso una entrega puede estar calificada y tarde a la vez
 * @param grade          null mientras no se haya corregido
 * @param points         sobre cuánto vale la tarea; hace falta para poder
 *                       normalizar la nota al calcular la media ponderada de
 *                       la clase, que mezcla tareas con puntuaciones distintas
 * @param weight         peso de la tarea en esa media
 * @param groupName      el subgrupo que la entregó, en una tarea grupal; null
 *                       en el resto
 */
public record SubmissionResponse(
        UUID id,
        UUID assignmentId,
        String assignmentTitle,
        UUID studentId,
        String studentName,
        String content,
        String fileUrl,
        String status,
        Instant submittedAt,
        boolean entregadaTarde,
        GradeResponse grade,
        String teacherNote,
        int points,
        BigDecimal weight,
        String groupName) {

    public static SubmissionResponse of(Submission submission, GradeResponse grade) {
        return new SubmissionResponse(
                submission.getId(),
                submission.getAssignment().getId(),
                submission.getAssignment().getTitle(),
                submission.getStudent().getId(),
                submission.getStudent().getName(),
                submission.getContent(),
                submission.getFileUrl(),
                submission.getStatus().getValue(),
                submission.getSubmittedAt(),
                submission.entregadaTarde(),
                grade,
                submission.getTeacherNote(),
                submission.getAssignment().getPoints(),
                submission.getAssignment().getWeight(),
                submission.getClassGroup() == null ? null : submission.getClassGroup().getName());
    }
}
