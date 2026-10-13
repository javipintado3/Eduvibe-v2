package com.eduvibe.model;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Qué opción marcó un alumno para una pregunta, dentro de un {@link ExamAttempt}. */
@Entity
@Table(name = "exam_answers")
@Getter
@Setter
@NoArgsConstructor
public class ExamAnswer {

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attempt_id", nullable = false)
    private ExamAttempt attempt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private ExamQuestion question;

    /** Null si la pregunta se quedó sin responder. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "selected_option_id")
    private ExamOption selectedOption;

    public ExamAnswer(ExamAttempt attempt, ExamQuestion question, ExamOption selectedOption) {
        this.attempt = attempt;
        this.question = question;
        this.selectedOption = selectedOption;
    }

    public boolean isCorrect() {
        return selectedOption != null && selectedOption.isCorrect();
    }
}
