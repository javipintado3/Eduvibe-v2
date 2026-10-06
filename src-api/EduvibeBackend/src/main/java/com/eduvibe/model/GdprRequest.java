package com.eduvibe.model;

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
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Constancia de que una persona pidió exportar o borrar sus datos, y de que se
 * atendió. Hoy ambas cosas se resuelven en el momento.
 */
@Entity
@Table(name = "gdpr_requests")
@Getter
@NoArgsConstructor
public class GdprRequest {

    public static final String EXPORTAR = "export";
    public static final String BORRAR = "erase";

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "type", nullable = false, length = 20)
    private String type;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Generated(event = EventType.INSERT)
    @Column(name = "requested_at", insertable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    private GdprRequest(User user, String type) {
        this.user = user;
        this.type = type;
        this.status = "completed";
        this.resolvedAt = Instant.now();
    }

    /** Solicitud que se atiende en el mismo momento en que se hace. */
    public static GdprRequest atendida(User user, String type) {
        return new GdprRequest(user, type);
    }
}
