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
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import com.eduvibe.dto.user.UpdateUserRequest;
import com.eduvibe.exception.BadRequestException;
import com.eduvibe.exception.ProtectedDemoAccountException;
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
    @Mock private PersonalDataService personalDataService;
    @Spy private ProtectedDemoAccounts protectedDemoAccounts = new ProtectedDemoAccounts("compartida@centro.es");

    @InjectMocks
    private UserService service;

    private final UUID idAdmin = UUID.randomUUID();
    private Organization organization;

    @BeforeEach
    void preparar() {
        organization = new Organization("Centro", null);
        organization.setId(UUID.randomUUID());
        when(authService.currentIdentity()).thenReturn(
                new AuthenticatedUser(idAdmin, "admin@centro.es", "Admin", UserRole.ADMIN, organization.getId()));
    }

    private User account(UserRole role, UserStatus status) {
        User user = new User(organization, "ana@centro.es", "Ana", role);
        user.setId(UUID.randomUUID());
        user.setStatus(status);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        return user;
    }

    @Nested
    @DisplayName("Eliminar un usuario")
    class Delete {

        @Test
        @DisplayName("elimina a una persona de su centro")
        void elimina() {
            User student = account(UserRole.STUDENT, UserStatus.ACTIVE);

            service.delete(student.getId());

            verify(personalDataService).deleteUser(student);
        }

        @Test
        @DisplayName("nadie puede eliminarse a sí mismo")
        void noUnoMismo() {
            assertThatThrownBy(() -> service.delete(idAdmin))
                    .isInstanceOf(BadRequestException.class);

            verify(personalDataService, never()).deleteUser(any());
        }

        @Test
        @DisplayName("no se puede eliminar al último administrador activo")
        void noElUltimoAdmin() {
            User otherAdmin = account(UserRole.ADMIN, UserStatus.ACTIVE);
            when(userRepository.countByOrganizationIdAndRoleAndStatus(organization.getId(), UserRole.ADMIN, UserStatus.ACTIVE))
                    .thenReturn(1L);

            assertThatThrownBy(() -> service.delete(otherAdmin.getId()))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("única persona administradora");

            verify(personalDataService, never()).deleteUser(any());
        }

        @Test
        @DisplayName("sí se puede eliminar a un administrador si queda otro activo")
        void adminSiQuedaOtro() {
            User otherAdmin = account(UserRole.ADMIN, UserStatus.ACTIVE);
            when(userRepository.countByOrganizationIdAndRoleAndStatus(organization.getId(), UserRole.ADMIN, UserStatus.ACTIVE))
                    .thenReturn(2L);

            service.delete(otherAdmin.getId());

            verify(personalDataService).deleteUser(otherAdmin);
        }

        @Test
        @DisplayName("un usuario de otro centro se trata como inexistente")
        void otroCentro() {
            Organization otherOrganization = new Organization("Otro", null);
            otherOrganization.setId(UUID.randomUUID());
            User foreignUser = new User(otherOrganization, "eva@otro.es", "Eva", UserRole.STUDENT);
            foreignUser.setId(UUID.randomUUID());
            when(userRepository.findById(foreignUser.getId())).thenReturn(Optional.of(foreignUser));

            assertThatThrownBy(() -> service.delete(foreignUser.getId()))
                    .isInstanceOf(NotFoundException.class);

            verify(personalDataService, never()).deleteUser(any());
        }

        @Test
        @DisplayName("no se elimina dos veces a la misma persona")
        void yaEliminada() {
            User student = account(UserRole.STUDENT, UserStatus.DISABLED);
            student.delete();

            assertThatThrownBy(() -> service.delete(student.getId()))
                    .isInstanceOf(NotFoundException.class);

            assertThat(student.isDeleted()).isTrue();
            verify(personalDataService, never()).deleteUser(any());
        }
    }

    @Nested
    @DisplayName("Cuentas compartidas de la demo")
    class SharedAccounts {

        private User shared() {
            User user = new User(organization, "compartida@centro.es", "Demo", UserRole.TEACHER);
            user.setId(UUID.randomUUID());
            user.setStatus(UserStatus.ACTIVE);
            when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
            return user;
        }

        @Test
        @DisplayName("no se puede eliminar")
        void noSeElimina() {
            User demo = shared();

            assertThatThrownBy(() -> service.delete(demo.getId()))
                    .isInstanceOf(ProtectedDemoAccountException.class);

            verify(personalDataService, never()).deleteUser(any());
        }

        @Test
        @DisplayName("no se puede desactivar")
        void noSeDesactiva() {
            User demo = shared();

            assertThatThrownBy(() -> service.changeStatus(demo.getId(), "disabled"))
                    .isInstanceOf(ProtectedDemoAccountException.class);

            assertThat(demo.getStatus()).isEqualTo(UserStatus.ACTIVE);
        }

        @Test
        @DisplayName("no se puede editar, ni su email ni su rol")
        void noSeEdita() {
            User demo = shared();

            assertThatThrownBy(() -> service.update(demo.getId(),
                    new UpdateUserRequest("Otro nombre", "otro@centro.es", "student")))
                    .isInstanceOf(ProtectedDemoAccountException.class);

            assertThat(demo.getEmail()).isEqualTo("compartida@centro.es");
        }
    }
}
