package com.eduvibe.dto.rubric;

import java.math.BigDecimal;
import java.util.UUID;

import com.eduvibe.model.RubricScore;

public record RubricScoreResponse(
        UUID criterionId,
        String description,
        BigDecimal maxPoints,
        BigDecimal points) {

    public static RubricScoreResponse of(RubricScore score) {
        return new RubricScoreResponse(
                score.getCriterion().getId(),
                score.getCriterion().getDescription(),
                score.getCriterion().getMaxPoints(),
                score.getPoints());
    }
}
