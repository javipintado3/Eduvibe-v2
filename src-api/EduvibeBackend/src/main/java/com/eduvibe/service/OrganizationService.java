package com.eduvibe.service;

import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.eduvibe.dto.organization.OrganizationResponse;
import com.eduvibe.exception.BadRequestException;
import com.eduvibe.exception.ConflictException;
import com.eduvibe.exception.NotFoundException;
import com.eduvibe.model.Organization;
import com.eduvibe.repository.OrganizationRepository;

import lombok.RequiredArgsConstructor;

/**
 * Ajustes del centro del administrador que tiene la sesión abierta.
 *
 * Hoy solo el dominio de correo permitido. Se aplica al dar de alta o editar
 * usuarios; las cuentas que ya existen con otro dominio no se tocan.
 */
@Service
@RequiredArgsConstructor
public class OrganizationService {

    /** Dos o más etiquetas separadas por puntos, como "iesalixar.edu". */
    private static final Pattern DOMAIN = Pattern.compile(
            "^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$");

    private final OrganizationRepository organizationRepository;
    private final AuthService authService;

    @Transactional(readOnly = true)
    public OrganizationResponse get() {
        return OrganizationResponse.of(currentOrganization());
    }

    @Transactional
    public OrganizationResponse changeAllowedDomain(String domain) {
        Organization organization = currentOrganization();
        String normalized = normalize(domain);

        // El dominio es único entre centros: si otro ya lo tiene, se avisa con
        // un mensaje claro en lugar de dejar que lo rechace la base de datos
        if (normalized != null && organizationRepository.findByAllowedDomain(normalized)
                .filter(other -> !other.getId().equals(organization.getId()))
                .isPresent()) {
            throw new ConflictException("Ese dominio ya está asignado a otro centro");
        }

        organization.setAllowedDomain(normalized);
        organizationRepository.save(organization);

        return OrganizationResponse.of(organization);
    }

    /** Vacío significa "sin restricción"; se admite que escriban "@dominio.es". */
    private String normalize(String domain) {
        if (domain == null || domain.isBlank()) {
            return null;
        }
        String clean = domain.trim().toLowerCase();
        if (clean.startsWith("@")) {
            clean = clean.substring(1);
        }
        if (!DOMAIN.matcher(clean).matches()) {
            throw new BadRequestException("El dominio no es válido. Ejemplo: iesalixar.edu");
        }
        return clean;
    }

    private Organization currentOrganization() {
        var id = authService.currentIdentity().organizationId();
        return organizationRepository.findById(id)
                .orElseThrow(() -> NotFoundException.of("Organización", id));
    }
}
