package com.eduvibe.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Calificación de una entrega.
 *
 * Está en su propia tabla, y no como dos columnas más de submissions, para
 * poder registrar quién corrige y cuándo. En un centro educativo eso importa:
 * una nota es un acto con responsable.
 *
 * La nota es BigDecimal y no double porque es un valor exacto que se compara y
 * se promedia, y los errores de redondeo del coma flotante no tienen cabida en
 * un boletín.
 */
@Entity
@Table(name = "grades")
@Getter
@Setter
@NoArgsConstructor
public class Grade {

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "submission_id", nullable = false, unique = true)
    private Submission submission;

    @Column(name = "score", nullable = false, precision = 5, scale = 2)
    private BigDecimal score;

    /**
     * La nota tal cual la escribió quien corrige, antes de cualquier descuento.
     *
     * Existe para poder precargar el formulario de corrección al reabrirlo: si
     * se usara {@code score} (ya penalizada) para eso, reguardar sin cambios
     * aplicaría el descuento por entrega tardía una segunda vez.
     */
    @Column(name = "raw_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal rawScore;

    @Column(name = "feedback")
    private String feedback;

    /** Si esta nota ya lleva aplicado el descuento por entrega tardía de la tarea. */
    @Column(name = "late_penalty_applied", nullable = false)
    private boolean latePenaltyApplied = false;

    /** Quién ha puesto la nota. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "graded_by", nullable = false)
    private User gradedBy;

    @Generated(event = EventType.INSERT)
    @Column(name = "graded_at", insertable = false, updatable = false)
    private Instant gradedAt;

    @Generated(event = { EventType.INSERT, EventType.UPDATE })
    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    public Grade(Submission submission, BigDecimal rawScore, BigDecimal score, String feedback, User gradedBy) {
        this.submission = submission;
        this.rawScore = rawScore;
        this.score = score;
        this.feedback = feedback;
        this.gradedBy = gradedBy;
    }

    /** Actualiza una nota ya puesta, dejando constancia de quién la revisa. */
    public void grade(BigDecimal rawScore, BigDecimal score, String feedback, User gradedBy,
                          boolean latePenaltyApplied) {
        this.rawScore = rawScore;
        this.score = score;
        this.feedback = feedback;
        this.gradedBy = gradedBy;
        this.latePenaltyApplied = latePenaltyApplied;
    }
}
