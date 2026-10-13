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
import lombok.Setter;

/**
 * Centro educativo. Es la raíz de todo: usuarios, clases y eventos cuelgan de
 * una organización, de modo que la misma instalación puede dar servicio a
 * varios centros sin que se vean entre sí.
 */
@Entity
@Table(name = "organizations")
@Getter
@Setter
@NoArgsConstructor
public class Organization {

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    /**
     * Dominio de correo permitido para las altas, por ejemplo "iesalixar.edu".
     * Si es null no hay restricción de dominio.
     */
    @Column(name = "allowed_domain")
    private String allowedDomain;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    public Organization(String name, String allowedDomain) {
        this.name = name;
        this.allowedDomain = allowedDomain;
    }

    /**
     * Indica si un email puede darse de alta en esta organización.
     */
    public boolean acceptsEmail(String email) {
        if (allowedDomain == null || allowedDomain.isBlank()) {
            return true;
        }
        return email != null && email.toLowerCase().endsWith("@" + allowedDomain.toLowerCase());
    }
}
