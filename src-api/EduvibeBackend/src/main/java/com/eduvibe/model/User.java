package com.eduvibe.model;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import com.eduvibe.model.enums.UserRole;
import com.eduvibe.model.enums.UserStatus;

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
 * Persona con acceso a la plataforma.
 *
 * Las cuentas no se crean solas: las da de alta un administrador y nacen en
 * estado PENDING y sin contraseña. La contraseña la establece la propia persona
 * al aceptar su invitación. Por eso passwordHash admite null.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "org_id", nullable = false)
    private Organization organization;

    /** Siempre en minúsculas: ver {@link #normalizarEmail(String)}. */
    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "role", nullable = false, length = 20)
    private UserRole role;

    @Column(name = "status", nullable = false, length = 20)
    private UserStatus status;

    /** Null mientras la persona no haya aceptado su invitación. */
    @Column(name = "password_hash")
    private String passwordHash;

    /**
     * Secreto del 2FA en Base32. Se rellena al empezar la configuración, pero
     * solo cuenta cuando {@code totpEnabled} es true. Tiene que poder leerse de
     * vuelta para verificar códigos, así que (a diferencia de la contraseña) no
     * se puede guardar como hash.
     */
    @Column(name = "totp_secret")
    private String totpSecret;

    @Column(name = "totp_enabled", nullable = false)
    private boolean totpEnabled;

    /** Null hasta que la persona sube una foto: mientras tanto se ven sus iniciales. */
    @Column(name = "avatar_url")
    private String avatarUrl;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Generated(event = { EventType.INSERT, EventType.UPDATE })
    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    public User(Organization organization, String email, String name, UserRole role) {
        this.organization = organization;
        this.email = normalizarEmail(email);
        this.name = name;
        this.role = role;
        this.status = UserStatus.PENDING;
    }

    public void setEmail(String email) {
        this.email = normalizarEmail(email);
    }

    /**
     * El email identifica de forma única a la persona dentro de su
     * organización, así que se guarda siempre en minúsculas y sin espacios. De
     * lo contrario "Ana@centro.es" y "ana@centro.es" serían dos cuentas.
     */
    public static String normalizarEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    /**
     * Marca la cuenta como activa con la contraseña que la persona acaba de
     * establecer al aceptar su invitación.
     */
    public void activarCon(String passwordHash) {
        this.passwordHash = passwordHash;
        this.status = UserStatus.ACTIVE;
    }

    /** Cambia la contraseña de una cuenta que ya existe, sin tocar su estado. */
    public void cambiarPassword(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    /** Solo las cuentas activas y con contraseña pueden iniciar sesión. */
    public boolean puedeIniciarSesion() {
        return status == UserStatus.ACTIVE && passwordHash != null;
    }
}
