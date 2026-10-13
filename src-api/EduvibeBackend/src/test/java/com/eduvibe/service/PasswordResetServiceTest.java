package com.eduvibe.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.eduvibe.config.AppProperties;
import com.eduvibe.exception.BadRequestException;
import com.eduvibe.model.Organization;
import com.eduvibe.model.PasswordReset;
import com.eduvibe.model.User;
import com.eduvibe.model.enums.UserRole;
import com.eduvibe.repository.PasswordResetRepository;
import com.eduvibe.repository.UserRepository;
import com.eduvibe.util.Tokens;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    private static final String EMAIL = "ana@centro.es";

    @Mock
    private PasswordResetRepository passwordResetRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private MailService mailService;

    private PasswordResetService service;

    @BeforeEach
    void crearServicio() {
        AppProperties properties = new AppProperties(null, null, "http://localhost:4200", null,
                null, new AppProperties.PasswordReset(60), null);
        service = new PasswordResetService(
                passwordResetRepository, userRepository, passwordEncoder, mailService, properties);
    }

    private User activeUser() {
        User user = new User(new Organization("Centro", null), EMAIL, "Ana", UserRole.STUDENT);
        user.enableWith("$2a$10$hashAntiguo");
        return user;
    }

    @Nested
    @DisplayName("Solicitar")
    class Request {

        @Test
        @DisplayName("con una cuenta activa guarda solo el hash del token y envía el enlace con el token en claro")
        void cuentaActiva() {
            User user = activeUser();
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

            service.request(EMAIL);

            ArgumentCaptor<PasswordReset> saved = ArgumentCaptor.forClass(PasswordReset.class);
            verify(passwordResetRepository).save(saved.capture());
            ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
            verify(mailService).sendReset(any(User.class), link.capture());

            String token = link.getValue().substring(link.getValue().lastIndexOf('/') + 1);
            assertThat(link.getValue()).startsWith("http://localhost:4200/restablecer/");
            assertThat(saved.getValue().getTokenHash()).isEqualTo(Tokens.hash(token));
            assertThat(saved.getValue().getTokenHash()).isNotEqualTo(token);
        }

        @Test
        @DisplayName("con un email inexistente no hace nada ni lanza error")
        void emailInexistente() {
            when(userRepository.findByEmail("no-existe@centro.es")).thenReturn(Optional.empty());

            service.request("no-existe@centro.es");

            verifyNoInteractions(passwordResetRepository, mailService);
        }

        @Test
        @DisplayName("con una cuenta pendiente de invitación no envía nada: la recuperación nunca activa cuentas")
        void cuentaPendiente() {
            User pending = new User(new Organization("Centro", null), EMAIL, "Ana", UserRole.STUDENT);
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(pending));

            service.request(EMAIL);

            verifyNoInteractions(passwordResetRepository, mailService);
        }

        @Test
        @DisplayName("si acaba de pedir otro enlace ignora la petición, para no llenar de correos la bandeja")
        void esperaEntrePeticiones() {
            User user = activeUser();
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
            when(passwordResetRepository.existsByUserIdAndCreatedAtAfter(any(), any(Instant.class)))
                    .thenReturn(true);

            service.request(EMAIL);

            verifyNoInteractions(mailService);
            verify(passwordResetRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Restablecer")
    class Reset {

        private PasswordReset validLink(User user) {
            return new PasswordReset(user, Tokens.hash("token-bueno"),
                    Instant.now().plusSeconds(600));
        }

        @Test
        @DisplayName("con un enlace válido cambia la contraseña e invalida los demás enlaces")
        void enlaceValido() {
            User user = activeUser();
            when(passwordResetRepository.findByTokenHash(Tokens.hash("token-bueno")))
                    .thenReturn(Optional.of(validLink(user)));
            when(passwordEncoder.encode("nueva-clave")).thenReturn("$2a$10$hashNuevo");

            service.reset("token-bueno", "nueva-clave");

            assertThat(user.getPasswordHash()).isEqualTo("$2a$10$hashNuevo");
            verify(userRepository).save(user);
            verify(passwordResetRepository).invalidatePendingFor(user.getId());
            assertThat(user.getTokenVersion()).isEqualTo(1);
        }

        @Test
        @DisplayName("con un token desconocido lanza el error genérico")
        void tokenDesconocido() {
            when(passwordResetRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.reset("inventado", "nueva-clave"))
                    .isInstanceOf(BadRequestException.class);

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("con un enlace caducado lanza el mismo error y no cambia la contraseña")
        void enlaceCaducado() {
            User user = activeUser();
            PasswordReset expired = new PasswordReset(user, Tokens.hash("token-viejo"),
                    Instant.now().minusSeconds(1));
            when(passwordResetRepository.findByTokenHash(Tokens.hash("token-viejo")))
                    .thenReturn(Optional.of(expired));

            assertThatThrownBy(() -> service.reset("token-viejo", "nueva-clave"))
                    .isInstanceOf(BadRequestException.class);

            assertThat(user.getPasswordHash()).isEqualTo("$2a$10$hashAntiguo");
        }

        @Test
        @DisplayName("con un enlace ya usado lanza el mismo error")
        void enlaceYaUsado() {
            User user = activeUser();
            PasswordReset used = validLink(user);
            used.markAsUsed();
            when(passwordResetRepository.findByTokenHash(Tokens.hash("token-bueno")))
                    .thenReturn(Optional.of(used));

            assertThatThrownBy(() -> service.reset("token-bueno", "nueva-clave"))
                    .isInstanceOf(BadRequestException.class);

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("si la cuenta se desactivó entre la petición y el uso, no cambia la contraseña")
        void cuentaDesactivada() {
            User user = activeUser();
            user.setStatus(com.eduvibe.model.enums.UserStatus.DISABLED);
            when(passwordResetRepository.findByTokenHash(Tokens.hash("token-bueno")))
                    .thenReturn(Optional.of(validLink(user)));

            assertThatThrownBy(() -> service.reset("token-bueno", "nueva-clave"))
                    .isInstanceOf(BadRequestException.class);

            verify(userRepository, never()).save(any());
        }
    }
}
