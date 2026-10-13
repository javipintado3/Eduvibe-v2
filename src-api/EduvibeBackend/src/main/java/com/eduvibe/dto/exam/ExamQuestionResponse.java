package com.eduvibe.dto.exam;

import java.util.List;
import java.util.UUID;

import com.eduvibe.model.ExamOption;
import com.eduvibe.model.ExamQuestion;

/**
 * Una pregunta con sus opciones y cuál es la correcta.
 *
 * Solo para el profesorado ({@code GET /api/exams/{id}/questions}), que es
 * quien puede ver la corrección. Lo que responde el alumnado mientras hace el
 * examen es {@link ExamAttemptResponse}, sin este dato.
 */
public record ExamQuestionResponse(UUID id, String text, int points, List<OptionResponse> options) {

    public static ExamQuestionResponse of(ExamQuestion question, List<ExamOption> options) {
        return new ExamQuestionResponse(
                question.getId(),
                question.getText(),
                question.getPoints(),
                options.stream().map(OptionResponse::of).toList());
    }

    public record OptionResponse(UUID id, String text, boolean correct) {

        public static OptionResponse of(ExamOption option) {
            return new OptionResponse(option.getId(), option.getText(), option.isCorrect());
        }
    }
}
