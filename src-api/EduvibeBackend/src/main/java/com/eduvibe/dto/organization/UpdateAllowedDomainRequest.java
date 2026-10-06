package com.eduvibe.dto.organization;

/**
 * Dominio de correo permitido para las altas, por ejemplo "iesalixar.edu".
 * Vacío o null quita la restricción.
 */
public record UpdateAllowedDomainRequest(String allowedDomain) {
}
