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
import lombok.Setter;

/**
 * Invitación de alta: el enlace de un solo uso con el que una persona establece
 * su contraseña y activa su cuenta.
 *
 * Se guarda el hash del token, nunca el token en claro. Quien pueda leer la
 * base de datos no puede usar las invitaciones pendientes, igual que no puede
 * usar las contraseñas.
 */
@Entity
@Table(name = "invitations")
@Getter
@Setter
@NoArgsConstructor
public class Invitation {

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** Null mientras no se haya consumido. */
    @Column(name = "used_at")
    private Instant usedAt;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    public Invitation(User user, String tokenHash, Instant expiresAt) {
        this.user = user;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    public boolean isUsed() {
        return usedAt != null;
    }

    public boolean hasExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    /** Una invitación sirve una sola vez y solo dentro de su plazo. */
    public boolean isUsable() {
        return !isUsed() && !hasExpired();
    }

    public void markAsUsed() {
        this.usedAt = Instant.now();
    }
}
