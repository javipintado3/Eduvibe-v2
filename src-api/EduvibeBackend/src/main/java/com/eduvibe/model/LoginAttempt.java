package com.eduvibe.model;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Un intento de inicio de sesión, correcto o fallido.
 *
 * Sirve para bloquear temporalmente un email tras varios fallos seguidos. La
 * IP se guarda solo como rastro de auditoría: no se bloquea por IP, porque
 * muchas personas comparten la misma detrás de una red de centro.
 */
@Entity
@Table(name = "login_attempts")
@Getter
@NoArgsConstructor
public class LoginAttempt {

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "succeeded", nullable = false)
    private boolean succeeded;

    @Column(name = "attempted_at", nullable = false)
    private Instant attemptedAt;

    public LoginAttempt(String email, String ipAddress, boolean succeeded) {
        this.email = email;
        this.ipAddress = ipAddress;
        this.succeeded = succeeded;
        this.attemptedAt = Instant.now();
    }
}
