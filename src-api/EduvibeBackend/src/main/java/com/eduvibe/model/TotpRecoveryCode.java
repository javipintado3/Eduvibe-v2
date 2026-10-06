package com.eduvibe.model;

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

/**
 * Código de recuperación de un solo uso, por si se pierde el móvil con la app
 * de autenticación. Se guarda el hash, nunca el código.
 */
@Entity
@Table(name = "totp_recovery_codes")
@Getter
@NoArgsConstructor
public class TotpRecoveryCode {

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "code_hash", nullable = false)
    private String codeHash;

    /** Null mientras no se haya gastado. */
    @Column(name = "used_at")
    private Instant usedAt;

    public TotpRecoveryCode(User user, String codeHash) {
        this.user = user;
        this.codeHash = codeHash;
    }

    public void marcarComoUsado() {
        this.usedAt = Instant.now();
    }
}
