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
    private static final Pattern DOMINIO = Pattern.compile(
            "^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$");

    private final OrganizationRepository organizationRepository;
    private final AuthService authService;

    @Transactional(readOnly = true)
    public OrganizationResponse obtener() {
        return OrganizationResponse.de(organizacionActual());
    }

    @Transactional
    public OrganizationResponse cambiarDominioPermitido(String dominio) {
        Organization organizacion = organizacionActual();
        String normalizado = normalizar(dominio);

        // El dominio es único entre centros: si otro ya lo tiene, se avisa con
        // un mensaje claro en lugar de dejar que lo rechace la base de datos
        if (normalizado != null && organizationRepository.findByAllowedDomain(normalizado)
                .filter(otra -> !otra.getId().equals(organizacion.getId()))
                .isPresent()) {
            throw new ConflictException("Ese dominio ya está asignado a otro centro");
        }

        organizacion.setAllowedDomain(normalizado);
        organizationRepository.save(organizacion);

        return OrganizationResponse.de(organizacion);
    }

    /** Vacío significa "sin restricción"; se admite que escriban "@dominio.es". */
    private String normalizar(String dominio) {
        if (dominio == null || dominio.isBlank()) {
            return null;
        }
        String limpio = dominio.trim().toLowerCase();
        if (limpio.startsWith("@")) {
            limpio = limpio.substring(1);
        }
        if (!DOMINIO.matcher(limpio).matches()) {
            throw new BadRequestException("El dominio no es válido. Ejemplo: iesalixar.edu");
        }
        return limpio;
    }

    private Organization organizacionActual() {
        var id = authService.identidadActual().organizationId();
        return organizationRepository.findById(id)
                .orElseThrow(() -> NotFoundException.de("Organización", id));
    }
}
