package com.eduvibe.dto.organization;

import java.util.UUID;

import com.eduvibe.model.Organization;

/**
 * Datos del centro que ve la administración. {@code allowedDomain} es null si
 * no hay restricción de dominio para las altas.
 */
public record OrganizationResponse(UUID id, String name, String allowedDomain) {

    public static OrganizationResponse de(Organization organizacion) {
        return new OrganizationResponse(organizacion.getId(), organizacion.getName(), organizacion.getAllowedDomain());
    }
}
