package com.eduvibe.model;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.generator.EventType;
import org.hibernate.type.SqlTypes;

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
 * Aviso dirigido a una persona: una tarea nueva, una nota publicada.
 *
 * El contenido variable va en un campo JSON en lugar de en columnas, porque
 * cada tipo necesita datos distintos —una tarea nueva lleva su identificador y
 * su título; una nota, además, la puntuación— y añadir una columna nueva por
 * cada tipo de aviso dejaría la tabla llena de huecos.
 */
@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
public class Notification {

    /** Tipos que la aplicación sabe emitir. */
    public static final String NEW_ASSIGNMENT = "assignment_created";
    public static final String PUBLISHED_GRADE = "grade_published";
    public static final String NEW_ANNOUNCEMENT = "announcement_created";
    public static final String NEW_EXAM = "exam_created";
    public static final String REGISTRATION_REQUESTED = "registration_requested";

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "type", nullable = false, length = 40)
    private String type;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload")
    private Map<String, Object> payload;

    /** Null mientras no se haya leído. */
    @Column(name = "read_at")
    private Instant readAt;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    public Notification(User user, String type, Map<String, Object> payload) {
        this.user = user;
        this.type = type;
        this.payload = payload;
    }

    public boolean isRead() {
        return readAt != null;
    }

    public void markAsRead() {
        if (readAt == null) {
            this.readAt = Instant.now();
        }
    }
}
