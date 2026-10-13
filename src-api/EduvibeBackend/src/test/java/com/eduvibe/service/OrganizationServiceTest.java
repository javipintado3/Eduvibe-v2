package com.eduvibe.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.eduvibe.dto.organization.OrganizationResponse;
import com.eduvibe.exception.BadRequestException;
import com.eduvibe.exception.ConflictException;
import com.eduvibe.model.Organization;
import com.eduvibe.model.enums.UserRole;
import com.eduvibe.repository.OrganizationRepository;
import com.eduvibe.security.AuthenticatedUser;

@ExtendWith(MockitoExtension.class)
class OrganizationServiceTest {

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private AuthService authService;

    private OrganizationService service;

    private final UUID organizationId = UUID.randomUUID();
    private Organization organization;

    @BeforeEach
    void preparar() {
        service = new OrganizationService(organizationRepository, authService);
        organization = new Organization("Centro", null);
        organization.setId(organizationId);
        when(authService.currentIdentity()).thenReturn(
                new AuthenticatedUser(UUID.randomUUID(), "admin@centro.es", "Admin", UserRole.ADMIN, organizationId));
        when(organizationRepository.findById(organizationId)).thenReturn(Optional.of(organization));
    }

    @Test
    @DisplayName("guarda el dominio normalizado: sin arroba inicial, sin espacios y en minúsculas")
    void normalizaElDominio() {
        OrganizationResponse answer = service.changeAllowedDomain("  @IESAlixar.EDU ");

        assertThat(answer.allowedDomain()).isEqualTo("iesalixar.edu");
        assertThat(organization.getAllowedDomain()).isEqualTo("iesalixar.edu");
        verify(organizationRepository).save(organization);
    }

    @Test
    @DisplayName("un valor vacío quita la restricción")
    void vacioQuitaLaRestriccion() {
        organization.setAllowedDomain("iesalixar.edu");

        service.changeAllowedDomain("  ");

        assertThat(organization.getAllowedDomain()).isNull();
    }

    @Test
    @DisplayName("rechaza un dominio con formato imposible")
    void rechazaFormatoInvalido() {
        assertThatThrownBy(() -> service.changeAllowedDomain("no es un dominio"))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.changeAllowedDomain("sinpunto"))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.changeAllowedDomain("uno@dos.es"))
                .isInstanceOf(BadRequestException.class);

        verify(organizationRepository, never()).save(organization);
    }

    @Test
    @DisplayName("avisa con claridad si otro centro ya tiene ese dominio")
    void dominioDeOtroCentro() {
        Organization other = new Organization("Otro", "iesalixar.edu");
        other.setId(UUID.randomUUID());
        when(organizationRepository.findByAllowedDomain("iesalixar.edu")).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.changeAllowedDomain("iesalixar.edu"))
                .isInstanceOf(ConflictException.class);

        verify(organizationRepository, never()).save(organization);
    }

    @Test
    @DisplayName("volver a guardar el dominio que ya tiene el propio centro no es un conflicto")
    void mismoDominioDelPropioCentro() {
        organization.setAllowedDomain("iesalixar.edu");
        when(organizationRepository.findByAllowedDomain("iesalixar.edu")).thenReturn(Optional.of(organization));

        service.changeAllowedDomain("iesalixar.edu");

        verify(organizationRepository).save(organization);
    }
}
