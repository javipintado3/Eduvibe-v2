package com.eduvibe.dto.rubric;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.eduvibe.model.Rubric;
import com.eduvibe.model.RubricCriterion;

public record RubricResponse(
        UUID id,
        List<CriterionResponse> criteria) {

    public static RubricResponse of(Rubric rubric, List<RubricCriterion> criteria) {
        return new RubricResponse(
                rubric.getId(),
                criteria.stream().map(CriterionResponse::of).toList());
    }

    public record CriterionResponse(
            UUID id,
            String description,
            BigDecimal maxPoints) {

        public static CriterionResponse of(RubricCriterion criterion) {
            return new CriterionResponse(criterion.getId(), criterion.getDescription(), criterion.getMaxPoints());
        }
    }
}
