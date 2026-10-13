package com.eduvibe.dto.submission;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.eduvibe.dto.rubric.RubricScoreResponse;
import com.eduvibe.model.Grade;

/**
 * Calificación de una entrega.
 *
 * Lleva quién corrige y cuándo porque una nota, en un centro educativo, es un
 * acto con responsable.
 *
 * @param rubricScores el desglose por criterio, cuando la tarea se calificó
 *                     con rúbrica; vacío si se calificó con una nota suelta
 */
public record GradeResponse(
        BigDecimal score,
        BigDecimal rawScore,
        String feedback,
        String gradedByName,
        Instant gradedAt,
        boolean latePenaltyApplied,
        List<RubricScoreResponse> rubricScores) {

    public static GradeResponse of(Grade grade, List<RubricScoreResponse> rubricScores) {
        return new GradeResponse(
                grade.getScore(),
                grade.getRawScore(),
                grade.getFeedback(),
                grade.getGradedBy().getName(),
                grade.getGradedAt(),
                grade.isLatePenaltyApplied(),
                rubricScores);
    }
}
