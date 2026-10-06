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

    private PasswordResetService servicio;

    @BeforeEach
    void crearServicio() {
        AppProperties propiedades = new AppProperties(null, null, "http://localhost:4200", null,
                null, new AppProperties.PasswordReset(60));
        servicio = new PasswordResetService(
                passwordResetRepository, userRepository, passwordEncoder, mailService, propiedades);
    }

    private User usuarioActivo() {
        User usuario = new User(new Organization("Centro", null), EMAIL, "Ana", UserRole.STUDENT);
        usuario.activarCon("$2a$10$hashAntiguo");
        return usuario;
    }

    @Nested
    @DisplayName("Solicitar")
    class Solicitar {

        @Test
        @DisplayName("con una cuenta activa guarda solo el hash del token y envía el enlace con el token en claro")
        void cuentaActiva() {
            User usuario = usuarioActivo();
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(usuario));

            servicio.solicitar(EMAIL);

            ArgumentCaptor<PasswordReset> guardado = ArgumentCaptor.forClass(PasswordReset.class);
            verify(passwordResetRepository).save(guardado.capture());
            ArgumentCaptor<String> enlace = ArgumentCaptor.forClass(String.class);
            verify(mailService).enviarRestablecimiento(any(User.class), enlace.capture());

            String token = enlace.getValue().substring(enlace.getValue().lastIndexOf('/') + 1);
            assertThat(enlace.getValue()).startsWith("http://localhost:4200/restablecer/");
            assertThat(guardado.getValue().getTokenHash()).isEqualTo(Tokens.hashear(token));
            assertThat(guardado.getValue().getTokenHash()).isNotEqualTo(token);
        }

        @Test
        @DisplayName("con un email inexistente no hace nada ni lanza error")
        void emailInexistente() {
            when(userRepository.findByEmail("no-existe@centro.es")).thenReturn(Optional.empty());

            servicio.solicitar("no-existe@centro.es");

            verifyNoInteractions(passwordResetRepository, mailService);
        }

        @Test
        @DisplayName("con una cuenta pendiente de invitación no envía nada: la recuperación nunca activa cuentas")
        void cuentaPendiente() {
            User pendiente = new User(new Organization("Centro", null), EMAIL, "Ana", UserRole.STUDENT);
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(pendiente));

            servicio.solicitar(EMAIL);

            verifyNoInteractions(passwordResetRepository, mailService);
        }

        @Test
        @DisplayName("si acaba de pedir otro enlace ignora la petición, para no llenar de correos la bandeja")
        void esperaEntrePeticiones() {
            User usuario = usuarioActivo();
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(usuario));
            when(passwordResetRepository.existsByUserIdAndCreatedAtAfter(any(), any(Instant.class)))
                    .thenReturn(true);

            servicio.solicitar(EMAIL);

            verifyNoInteractions(mailService);
            verify(passwordResetRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Restablecer")
    class Restablecer {

        private PasswordReset enlaceVigente(User usuario) {
            return new PasswordReset(usuario, Tokens.hashear("token-bueno"),
                    Instant.now().plusSeconds(600));
        }

        @Test
        @DisplayName("con un enlace válido cambia la contraseña e invalida los demás enlaces")
        void enlaceValido() {
            User usuario = usuarioActivo();
            when(passwordResetRepository.findByTokenHash(Tokens.hashear("token-bueno")))
                    .thenReturn(Optional.of(enlaceVigente(usuario)));
            when(passwordEncoder.encode("nueva-clave")).thenReturn("$2a$10$hashNuevo");

            servicio.restablecer("token-bueno", "nueva-clave");

            assertThat(usuario.getPasswordHash()).isEqualTo("$2a$10$hashNuevo");
            verify(userRepository).save(usuario);
            verify(passwordResetRepository).invalidarPendientesDe(usuario.getId());
        }

        @Test
        @DisplayName("con un token desconocido lanza el error genérico")
        void tokenDesconocido() {
            when(passwordResetRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> servicio.restablecer("inventado", "nueva-clave"))
                    .isInstanceOf(BadRequestException.class);

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("con un enlace caducado lanza el mismo error y no cambia la contraseña")
        void enlaceCaducado() {
            User usuario = usuarioActivo();
            PasswordReset caducado = new PasswordReset(usuario, Tokens.hashear("token-viejo"),
                    Instant.now().minusSeconds(1));
            when(passwordResetRepository.findByTokenHash(Tokens.hashear("token-viejo")))
                    .thenReturn(Optional.of(caducado));

            assertThatThrownBy(() -> servicio.restablecer("token-viejo", "nueva-clave"))
                    .isInstanceOf(BadRequestException.class);

            assertThat(usuario.getPasswordHash()).isEqualTo("$2a$10$hashAntiguo");
        }

        @Test
        @DisplayName("con un enlace ya usado lanza el mismo error")
        void enlaceYaUsado() {
            User usuario = usuarioActivo();
            PasswordReset usado = enlaceVigente(usuario);
            usado.marcarComoUsado();
            when(passwordResetRepository.findByTokenHash(Tokens.hashear("token-bueno")))
                    .thenReturn(Optional.of(usado));

            assertThatThrownBy(() -> servicio.restablecer("token-bueno", "nueva-clave"))
                    .isInstanceOf(BadRequestException.class);

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("si la cuenta se desactivó entre la petición y el uso, no cambia la contraseña")
        void cuentaDesactivada() {
            User usuario = usuarioActivo();
            usuario.setStatus(com.eduvibe.model.enums.UserStatus.DISABLED);
            when(passwordResetRepository.findByTokenHash(Tokens.hashear("token-bueno")))
                    .thenReturn(Optional.of(enlaceVigente(usuario)));

            assertThatThrownBy(() -> servicio.restablecer("token-bueno", "nueva-clave"))
                    .isInstanceOf(BadRequestException.class);

            verify(userRepository, never()).save(any());
        }
    }
}
