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

    private OrganizationService servicio;

    private final UUID idCentro = UUID.randomUUID();
    private Organization centro;

    @BeforeEach
    void preparar() {
        servicio = new OrganizationService(organizationRepository, authService);
        centro = new Organization("Centro", null);
        centro.setId(idCentro);
        when(authService.identidadActual()).thenReturn(
                new AuthenticatedUser(UUID.randomUUID(), "admin@centro.es", "Admin", UserRole.ADMIN, idCentro));
        when(organizationRepository.findById(idCentro)).thenReturn(Optional.of(centro));
    }

    @Test
    @DisplayName("guarda el dominio normalizado: sin arroba inicial, sin espacios y en minúsculas")
    void normalizaElDominio() {
        OrganizationResponse respuesta = servicio.cambiarDominioPermitido("  @IESAlixar.EDU ");

        assertThat(respuesta.allowedDomain()).isEqualTo("iesalixar.edu");
        assertThat(centro.getAllowedDomain()).isEqualTo("iesalixar.edu");
        verify(organizationRepository).save(centro);
    }

    @Test
    @DisplayName("un valor vacío quita la restricción")
    void vacioQuitaLaRestriccion() {
        centro.setAllowedDomain("iesalixar.edu");

        servicio.cambiarDominioPermitido("  ");

        assertThat(centro.getAllowedDomain()).isNull();
    }

    @Test
    @DisplayName("rechaza un dominio con formato imposible")
    void rechazaFormatoInvalido() {
        assertThatThrownBy(() -> servicio.cambiarDominioPermitido("no es un dominio"))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> servicio.cambiarDominioPermitido("sinpunto"))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> servicio.cambiarDominioPermitido("uno@dos.es"))
                .isInstanceOf(BadRequestException.class);

        verify(organizationRepository, never()).save(centro);
    }

    @Test
    @DisplayName("avisa con claridad si otro centro ya tiene ese dominio")
    void dominioDeOtroCentro() {
        Organization otro = new Organization("Otro", "iesalixar.edu");
        otro.setId(UUID.randomUUID());
        when(organizationRepository.findByAllowedDomain("iesalixar.edu")).thenReturn(Optional.of(otro));

        assertThatThrownBy(() -> servicio.cambiarDominioPermitido("iesalixar.edu"))
                .isInstanceOf(ConflictException.class);

        verify(organizationRepository, never()).save(centro);
    }

    @Test
    @DisplayName("volver a guardar el dominio que ya tiene el propio centro no es un conflicto")
    void mismoDominioDelPropioCentro() {
        centro.setAllowedDomain("iesalixar.edu");
        when(organizationRepository.findByAllowedDomain("iesalixar.edu")).thenReturn(Optional.of(centro));

        servicio.cambiarDominioPermitido("iesalixar.edu");

        verify(organizationRepository).save(centro);
    }
}
