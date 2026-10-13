package com.eduvibe.model;

import java.math.BigDecimal;
import java.time.Instant;
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

/**
 * El intento de un alumno en un examen: cuándo lo empezó, cuándo lo entregó
 * y qué nota sacó.
 *
 * No guarda un estado "en curso"/"entregado": se lee comparando submittedAt
 * con null, igual que una entrega tardía se calcula comparando fechas en vez
 * de guardar un booleano que podría dejar de coincidir con la realidad.
 */
@Entity
@Table(name = "exam_attempts")
@Getter
@Setter
@NoArgsConstructor
public class ExamAttempt {

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exam_id", nullable = false)
    private Exam exam;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    /** Null mientras no se haya entregado. */
    @Column(name = "submitted_at")
    private Instant submittedAt;

    /** Null hasta que se entrega; a partir de ahí, la corrección automática ya está hecha. */
    @Column(name = "score")
    private BigDecimal score;

    public ExamAttempt(Exam exam, User student) {
        this.exam = exam;
        this.student = student;
        this.startedAt = Instant.now();
    }

    public boolean isSubmitted() {
        return submittedAt != null;
    }

    /** Momento en el que se acaba el tiempo, a partir de cuándo se empezó y la duración del examen. */
    public Instant timeLimit() {
        return startedAt.plusSeconds(exam.getDurationMinutes() * 60L);
    }

    public void submit(BigDecimal score) {
        this.submittedAt = Instant.now();
        this.score = score;
    }
}
