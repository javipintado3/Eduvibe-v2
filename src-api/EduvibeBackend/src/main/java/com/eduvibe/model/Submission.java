package com.eduvibe.model;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import com.eduvibe.model.enums.SubmissionStatus;

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
 * Lo que un alumno concreto hace con una tarea concreta.
 *
 * Hay como mucho una por pareja tarea-alumno, y así lo garantiza una
 * restricción única en la base de datos.
 */
@Entity
@Table(name = "submissions")
@Getter
@Setter
@NoArgsConstructor
public class Submission {

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assignment_id", nullable = false)
    private Assignment assignment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    /** Respuesta escrita. */
    @Column(name = "content")
    private String content;

    @Column(name = "file_url")
    private String fileUrl;

    @Column(name = "status", nullable = false, length = 20)
    private SubmissionStatus status;

    /** Nota rápida del profesorado, independiente de la calificación: no hace falta poner nota para dejarla. */
    @Column(name = "teacher_note")
    private String teacherNote;

    /** Momento del envío. Null mientras siga siendo un borrador. */
    @Column(name = "submitted_at")
    private Instant submittedAt;

    /**
     * El subgrupo que entregó, en una tarea grupal. Null en el resto: la
     * entrega es de ese alumno y punto.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "class_group_id")
    private ClassGroup classGroup;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Generated(event = { EventType.INSERT, EventType.UPDATE })
    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    public Submission(Assignment assignment, User student) {
        this.assignment = assignment;
        this.student = student;
        this.status = SubmissionStatus.DRAFT;
    }

    /** Guarda el trabajo sin enviarlo: el alumno puede seguir después. */
    public void saveDraft(String content, String fileUrl) {
        this.content = content;
        this.fileUrl = fileUrl;
        this.status = SubmissionStatus.DRAFT;
        this.submittedAt = null;
    }

    /**
     * Envía la entrega. La base de datos exige fecha de envío en todo lo que no
     * sea un borrador, así que se fija aquí y no se deja a criterio de quien
     * llame.
     */
    public void enviar(String content, String fileUrl) {
        enviar(content, fileUrl, Instant.now());
    }

    /**
     * Igual que {@link #enviar(String, String)}, pero con el momento del envío
     * fijado desde fuera: en una tarea grupal, todas las entregas del mismo
     * subgrupo tienen que compartir literalmente el mismo instante para que
     * "llegó tarde" no pueda dar una respuesta distinta según a qué miembro se
     * le pregunte.
     */
    public void enviar(String content, String fileUrl, Instant moment) {
        this.content = content;
        this.fileUrl = fileUrl;
        this.status = SubmissionStatus.SUBMITTED;
        this.submittedAt = moment;
    }

    public void markAsGraded() {
        this.status = SubmissionStatus.GRADED;
    }

    public boolean isDraft() {
        return status == SubmissionStatus.DRAFT;
    }

    public boolean isGraded() {
        return status == SubmissionStatus.GRADED;
    }

    /**
     * Si llegó fuera de plazo. Se calcula, no se guarda: así una entrega puede
     * estar calificada y haber llegado tarde a la vez.
     */
    public boolean entregadaTarde() {
        return assignment.lateSubmission(submittedAt);
    }
}
