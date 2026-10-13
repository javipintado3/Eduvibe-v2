package com.eduvibe.model;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import com.eduvibe.model.enums.RegistrationStatus;

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
 * Petición de alta hecha por la propia persona, a la espera de que un
 * administrador la acepte.
 *
 * No es una cuenta: hasta que se aprueba no existe ningún {@link User}, así que
 * una solicitud falsa o sin verificar no puede iniciar sesión ni ocupa el
 * email. Tampoco guarda contraseña ni rol: la contraseña la elige la persona al
 * aceptar su invitación y el rol lo fija quien aprueba, para que nadie pueda
 * pedirse a sí mismo ser administrador.
 *
 * Del token de verificación solo se guarda su hash, igual que en las
 * invitaciones.
 */
@Entity
@Table(name = "registration_requests")
@Getter
@Setter
@NoArgsConstructor
public class RegistrationRequest {

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "org_id", nullable = false)
    private Organization organization;

    /** Siempre en minúsculas: ver {@link User#normalizeEmail(String)}. */
    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "status", nullable = false, length = 20)
    private RegistrationStatus status;

    /** Null una vez verificado el correo. */
    @Column(name = "token_hash")
    private String tokenHash;

    @Column(name = "token_expires_at")
    private Instant tokenExpiresAt;

    /** Texto libre opcional de quien solicita, para que la administración sepa quién es. */
    @Column(name = "message", length = 500)
    private String message;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Generated(event = { EventType.INSERT, EventType.UPDATE })
    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    public RegistrationRequest(Organization organization, String email, String name, String message,
                               String ipAddress) {
        this.organization = organization;
        this.email = User.normalizeEmail(email);
        this.name = name;
        this.message = message;
        this.ipAddress = ipAddress;
        this.status = RegistrationStatus.UNVERIFIED;
    }

    /** Guarda el token (su hash) con el que la persona confirmará su correo. */
    public void issueVerification(String tokenHash, Instant expiry) {
        this.tokenHash = tokenHash;
        this.tokenExpiresAt = expiry;
    }

    public boolean isUnverified() {
        return status == RegistrationStatus.UNVERIFIED;
    }

    public boolean isPending() {
        return status == RegistrationStatus.PENDING;
    }

    /** El enlace sirve una sola vez, mientras la solicitud no esté verificada y dentro de su plazo. */
    public boolean canBeVerified() {
        return isUnverified() && tokenHash != null && tokenExpiresAt != null
                && Instant.now().isBefore(tokenExpiresAt);
    }

    /** Correo confirmado: pasa a la cola del administrador y el token deja de valer. */
    public void markAsVerified() {
        this.status = RegistrationStatus.PENDING;
        this.verifiedAt = Instant.now();
        this.tokenHash = null;
        this.tokenExpiresAt = null;
    }

    public void approve(User administrator) {
        resolve(RegistrationStatus.APPROVED, administrator);
    }

    public void reject(User administrator) {
        resolve(RegistrationStatus.REJECTED, administrator);
    }

    private void resolve(RegistrationStatus result, User administrator) {
        this.status = result;
        this.reviewedAt = Instant.now();
        this.reviewedBy = administrator;
    }
}
