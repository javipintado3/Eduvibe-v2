package com.eduvibe.dto.exam;

import java.util.List;
import java.util.UUID;

import com.eduvibe.model.ExamOption;
import com.eduvibe.model.ExamQuestion;

/**
 * Una pregunta ya usada en algún examen de la clase, para reutilizarla en uno
 * nuevo. Lleva la respuesta correcta marcada porque solo se ofrece al
 * profesorado, igual que {@link ExamQuestionResponse}.
 */
public record BankQuestionResponse(UUID id, String text, int points, String examTitle, List<OptionResponse> options) {

    public static BankQuestionResponse of(ExamQuestion question, List<ExamOption> options) {
        return new BankQuestionResponse(
                question.getId(),
                question.getText(),
                question.getPoints(),
                question.getExam().getTitle(),
                options.stream().map(OptionResponse::of).toList());
    }

    public record OptionResponse(UUID id, String text, boolean correct) {

        public static OptionResponse of(ExamOption option) {
            return new OptionResponse(option.getId(), option.getText(), option.isCorrect());
        }
    }
}
