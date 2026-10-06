package com.eduvibe.model;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Un token concreto que se ha revocado, normalmente al cerrar sesión.
 *
 * Solo hace falta recordarlo hasta que el token caduque por sí solo: pasada esa
 * fecha ya no valdría de todos modos, y la aplicación purga la fila.
 */
@Entity
@Table(name = "revoked_tokens")
@Getter
@NoArgsConstructor
public class RevokedToken {

    /** Identificador del token (el jti del JWT). */
    @Id
    @Column(name = "jti", updatable = false, nullable = false)
    private UUID jti;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Generated(event = EventType.INSERT)
    @Column(name = "revoked_at", insertable = false, updatable = false)
    private Instant revokedAt;

    public RevokedToken(UUID jti, UUID userId, Instant expiresAt) {
        this.jti = jti;
        this.userId = userId;
        this.expiresAt = expiresAt;
    }
}
