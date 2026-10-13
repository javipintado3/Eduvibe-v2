package com.eduvibe.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.eduvibe.exception.BadRequestException;
import com.eduvibe.exception.NotFoundException;
import com.eduvibe.model.Organization;
import com.eduvibe.model.User;
import com.eduvibe.model.enums.UserRole;
import com.eduvibe.model.enums.UserStatus;
import com.eduvibe.repository.EnrollmentRepository;
import com.eduvibe.repository.OrganizationRepository;
import com.eduvibe.repository.UserRepository;
import com.eduvibe.security.AuthenticatedUser;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private OrganizationRepository organizationRepository;
    @Mock private EnrollmentRepository enrollmentRepository;
    @Mock private InvitationService invitationService;
    @Mock private AuthService authService;
    @Mock private DatosPersonalesService datosPersonalesService;

    @InjectMocks
    private UserService servicio;

    private final UUID idAdmin = UUID.randomUUID();
    private Organization centro;

    @BeforeEach
    void preparar() {
        centro = new Organization("Centro", null);
        centro.setId(UUID.randomUUID());
        when(authService.identidadActual()).thenReturn(
                new AuthenticatedUser(idAdmin, "admin@centro.es", "Admin", UserRole.ADMIN, centro.getId()));
    }

    private User cuenta(UserRole rol, UserStatus estado) {
        User usuario = new User(centro, "ana@centro.es", "Ana", rol);
        usuario.setId(UUID.randomUUID());
        usuario.setStatus(estado);
        when(userRepository.findById(usuario.getId())).thenReturn(Optional.of(usuario));
        return usuario;
    }

    @Nested
    @DisplayName("Eliminar un usuario")
    class Eliminar {

        @Test
        @DisplayName("elimina a una persona de su centro")
        void elimina() {
            User alumna = cuenta(UserRole.STUDENT, UserStatus.ACTIVE);

            servicio.eliminar(alumna.getId());

            verify(datosPersonalesService).eliminarUsuario(alumna);
        }

        @Test
        @DisplayName("nadie puede eliminarse a sí mismo")
        void noUnoMismo() {
            assertThatThrownBy(() -> servicio.eliminar(idAdmin))
                    .isInstanceOf(BadRequestException.class);

            verify(datosPersonalesService, never()).eliminarUsuario(any());
        }

        @Test
        @DisplayName("no se puede eliminar al último administrador activo")
        void noElUltimoAdmin() {
            User otroAdmin = cuenta(UserRole.ADMIN, UserStatus.ACTIVE);
            when(userRepository.countByOrganizationIdAndRoleAndStatus(centro.getId(), UserRole.ADMIN, UserStatus.ACTIVE))
                    .thenReturn(1L);

            assertThatThrownBy(() -> servicio.eliminar(otroAdmin.getId()))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("única persona administradora");

            verify(datosPersonalesService, never()).eliminarUsuario(any());
        }

        @Test
        @DisplayName("sí se puede eliminar a un administrador si queda otro activo")
        void adminSiQuedaOtro() {
            User otroAdmin = cuenta(UserRole.ADMIN, UserStatus.ACTIVE);
            when(userRepository.countByOrganizationIdAndRoleAndStatus(centro.getId(), UserRole.ADMIN, UserStatus.ACTIVE))
                    .thenReturn(2L);

            servicio.eliminar(otroAdmin.getId());

            verify(datosPersonalesService).eliminarUsuario(otroAdmin);
        }

        @Test
        @DisplayName("un usuario de otro centro se trata como inexistente")
        void otroCentro() {
            Organization otro = new Organization("Otro", null);
            otro.setId(UUID.randomUUID());
            User ajena = new User(otro, "eva@otro.es", "Eva", UserRole.STUDENT);
            ajena.setId(UUID.randomUUID());
            when(userRepository.findById(ajena.getId())).thenReturn(Optional.of(ajena));

            assertThatThrownBy(() -> servicio.eliminar(ajena.getId()))
                    .isInstanceOf(NotFoundException.class);

            verify(datosPersonalesService, never()).eliminarUsuario(any());
        }

        @Test
        @DisplayName("no se elimina dos veces a la misma persona")
        void yaEliminada() {
            User alumna = cuenta(UserRole.STUDENT, UserStatus.DISABLED);
            alumna.eliminar();

            assertThatThrownBy(() -> servicio.eliminar(alumna.getId()))
                    .isInstanceOf(NotFoundException.class);

            assertThat(alumna.estaEliminado()).isTrue();
            verify(datosPersonalesService, never()).eliminarUsuario(any());
        }
    }
}
