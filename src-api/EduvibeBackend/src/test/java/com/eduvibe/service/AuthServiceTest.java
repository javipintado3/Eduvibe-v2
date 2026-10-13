package com.eduvibe.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.eduvibe.dto.auth.AuthResponse;
import com.eduvibe.dto.auth.ChangePasswordRequest;
import com.eduvibe.dto.auth.LoginRequest;
import com.eduvibe.exception.AccountDisabledException;
import com.eduvibe.exception.BadRequestException;
import com.eduvibe.exception.TooManyRequestsException;
import com.eduvibe.exception.TwoFactorRequiredException;
import com.eduvibe.model.Organization;
import com.eduvibe.model.User;
import com.eduvibe.model.enums.UserRole;
import com.eduvibe.model.enums.UserStatus;
import com.eduvibe.repository.UserRepository;
import com.eduvibe.security.AuthenticatedUser;
import com.eduvibe.security.JwtService;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String MESSAGE_INVALID_CREDENTIALS =
            "Usuario y/o contraseña incorrectos. Si aún no has activado tu cuenta, abre el enlace de invitación que recibiste por correo.";
    private static final String IP = "127.0.0.1";

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private InvitationService invitationService;

    @Mock
    private LoginAttemptService loginAttemptService;

    @Mock
    private TwoFactorService twoFactorService;

    private AuthService authService;

    @BeforeEach
    void crearServicio() {
        authService = new AuthService(userRepository, passwordEncoder, new ProtectedDemoAccounts(""), jwtService, invitationService, loginAttemptService, twoFactorService);
    }

    private User activeUser(String email, String passwordHash) {
        User user = new User(new Organization("Centro", null), email, "Ana", UserRole.STUDENT);
        user.enableWith(passwordHash);
        return user;
    }

    @Nested
    @DisplayName("Login")
    class Login {

        @Test
        @DisplayName("con email inexistente lanza el mismo error que una contraseña incorrecta")
        void emailInexistente() {
            when(userRepository.findByEmail("no-existe@centro.es")).thenReturn(Optional.empty());
            when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

            assertThatThrownBy(() -> authService.login(new LoginRequest("no-existe@centro.es", "cualquiera"), IP))
                    .isInstanceOf(BadCredentialsException.class)
                    .hasMessage(MESSAGE_INVALID_CREDENTIALS);
        }

        @Test
        @DisplayName("con email inexistente comprueba igualmente una contraseña, para que el tiempo de "
                + "respuesta no delate qué emails están dados de alta")
        void emailInexistenteNoAtajaLaComprobacionDeContrasena() {
            when(userRepository.findByEmail("no-existe@centro.es")).thenReturn(Optional.empty());
            when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

            assertThatThrownBy(() -> authService.login(new LoginRequest("no-existe@centro.es", "cualquiera"), IP));

            // BCrypt.matches() es la operación lenta a propósito. Si se salta
            // cuando el usuario no existe, esa petición responde más rápido que
            // una con email real y contraseña incorrecta, y esa diferencia de
            // tiempo delata qué direcciones están registradas aunque el mensaje
            // de error sea idéntico en los dos casos.
            verify(passwordEncoder, times(1)).matches(anyString(), anyString());
        }

        @Test
        @DisplayName("con contraseña incorrecta lanza el mismo error")
        void contrasenaIncorrecta() {
            User user = activeUser("ana@centro.es", "$2a$10$hash");
            when(userRepository.findByEmail("ana@centro.es")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("mala", "$2a$10$hash")).thenReturn(false);

            assertThatThrownBy(() -> authService.login(new LoginRequest("ana@centro.es", "mala"), IP))
                    .isInstanceOf(BadCredentialsException.class)
                    .hasMessage(MESSAGE_INVALID_CREDENTIALS);
        }

        @Test
        @DisplayName("con cuenta pendiente de invitación lanza el mismo error aunque la contraseña sea correcta")
        void cuentaPendiente() {
            User user = new User(new Organization("Centro", null), "pendiente@centro.es", "Pendiente", UserRole.STUDENT);
            when(userRepository.findByEmail("pendiente@centro.es")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);

            assertThatThrownBy(() -> authService.login(new LoginRequest("pendiente@centro.es", "loQueSea"), IP))
                    .isInstanceOf(BadCredentialsException.class)
                    .hasMessage(MESSAGE_INVALID_CREDENTIALS);
        }

        @Test
        @DisplayName("con cuenta desactivada y la contraseña correcta avisa de que está desactivada, sin contarlo como fallo")
        void cuentaDesactivadaConContrasenaCorrecta() {
            User user = activeUser("ana@centro.es", "$2a$10$hash");
            user.setStatus(UserStatus.DISABLED);
            when(userRepository.findByEmail("ana@centro.es")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("correcta", "$2a$10$hash")).thenReturn(true);

            assertThatThrownBy(() -> authService.login(new LoginRequest("ana@centro.es", "correcta"), IP))
                    .isInstanceOf(AccountDisabledException.class)
                    .hasMessageContaining("desactivada");

            verify(loginAttemptService, never()).recordFailure(anyString(), anyString());
        }

        @Test
        @DisplayName("con cuenta desactivada y contraseña incorrecta da el error genérico: no delata que la cuenta existe")
        void cuentaDesactivadaConContrasenaIncorrecta() {
            User user = activeUser("ana@centro.es", "$2a$10$hash");
            user.setStatus(UserStatus.DISABLED);
            when(userRepository.findByEmail("ana@centro.es")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("mala", "$2a$10$hash")).thenReturn(false);

            assertThatThrownBy(() -> authService.login(new LoginRequest("ana@centro.es", "mala"), IP))
                    .isInstanceOf(BadCredentialsException.class)
                    .hasMessage(MESSAGE_INVALID_CREDENTIALS);
            verify(loginAttemptService).recordFailure("ana@centro.es", IP);
        }

        @Test
        @DisplayName("con el email bloqueado rechaza el intento sin llegar a mirar usuario ni contraseña")
        void emailBloqueado() {
            when(loginAttemptService.isLocked("ana@centro.es")).thenReturn(true);
            when(loginAttemptService.minutesOfLockout()).thenReturn(15L);

            assertThatThrownBy(() -> authService.login(new LoginRequest("ana@centro.es", "correcta"), IP))
                    .isInstanceOf(TooManyRequestsException.class)
                    .hasMessageContaining("15 minutos");

            verifyNoInteractions(userRepository, passwordEncoder);
        }

        @Test
        @DisplayName("apunta el fallo, también cuando el email no existe, para que el bloqueo no delate "
                + "qué emails están dados de alta")
        void apuntaElFalloDeUnEmailInexistente() {
            when(userRepository.findByEmail("no-existe@centro.es")).thenReturn(Optional.empty());
            when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

            assertThatThrownBy(() -> authService.login(new LoginRequest("no-existe@centro.es", "x"), IP));

            verify(loginAttemptService).recordFailure("no-existe@centro.es", IP);
        }

        @Test
        @DisplayName("apunta el acceso correcto, que deja el contador de fallos a cero")
        void apuntaElExito() {
            User user = activeUser("ana@centro.es", "$2a$10$hash");
            when(userRepository.findByEmail("ana@centro.es")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("correcta", "$2a$10$hash")).thenReturn(true);
            when(jwtService.issueFor(user)).thenReturn("token-emitido");
            when(jwtService.expiryOfTokenNew()).thenReturn(Instant.MAX);

            authService.login(new LoginRequest("ana@centro.es", "correcta"), IP);

            verify(loginAttemptService).recordSuccess("ana@centro.es", IP);
            verify(loginAttemptService, never()).recordFailure(anyString(), anyString());
        }

        private User userWithTwoFactor() {
            User user = activeUser("ana@centro.es", "$2a$10$hash");
            user.setTotpEnabled(true);
            when(userRepository.findByEmail("ana@centro.es")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("correcta", "$2a$10$hash")).thenReturn(true);
            return user;
        }

        @Test
        @DisplayName("con 2FA activado y sin código pide el código, sin contarlo como fallo")
        void dosPasosSinCodigo() {
            userWithTwoFactor();

            assertThatThrownBy(() -> authService.login(new LoginRequest("ana@centro.es", "correcta"), IP))
                    .isInstanceOf(TwoFactorRequiredException.class);

            verify(loginAttemptService, never()).recordFailure(anyString(), anyString());
            verify(loginAttemptService, never()).recordSuccess(anyString(), anyString());
        }

        @Test
        @DisplayName("con 2FA activado y un código incorrecto lo trata como credenciales inválidas y apunta el fallo")
        void dosPasosCodigoIncorrecto() {
            User user = userWithTwoFactor();
            when(twoFactorService.validCode(user, "000000")).thenReturn(false);

            assertThatThrownBy(() -> authService.login(
                    new LoginRequest("ana@centro.es", "correcta", "000000"), IP))
                    .isInstanceOf(BadCredentialsException.class);

            verify(loginAttemptService).recordFailure("ana@centro.es", IP);
        }

        @Test
        @DisplayName("con 2FA activado y el código correcto inicia sesión")
        void dosPasosCodigoCorrecto() {
            User user = userWithTwoFactor();
            when(twoFactorService.validCode(user, "123456")).thenReturn(true);
            when(jwtService.issueFor(user)).thenReturn("token-emitido");
            when(jwtService.expiryOfTokenNew()).thenReturn(Instant.MAX);

            AuthResponse answer = authService.login(
                    new LoginRequest("ana@centro.es", "correcta", "123456"), IP);

            assertThat(answer.token()).isEqualTo("token-emitido");
            verify(loginAttemptService).recordSuccess("ana@centro.es", IP);
        }

        @Test
        @DisplayName("con credenciales correctas devuelve la sesión iniciada")
        void credencialesCorrectas() {
            User user = activeUser("ana@centro.es", "$2a$10$hash");
            when(userRepository.findByEmail("ana@centro.es")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("correcta", "$2a$10$hash")).thenReturn(true);
            when(jwtService.issueFor(user)).thenReturn("token-emitido");
            when(jwtService.expiryOfTokenNew()).thenReturn(Instant.MAX);

            AuthResponse answer = authService.login(new LoginRequest("ana@centro.es", "correcta"), IP);

            assertThat(answer.token()).isEqualTo("token-emitido");
            assertThat(answer.user().email()).isEqualTo("ana@centro.es");
        }
    }

    @Nested
    @DisplayName("Cambiar contraseña")
    class ChangePassword {

        private final UUID userId = UUID.randomUUID();
        private User user;

        @BeforeEach
        void iniciarSesion() {
            user = activeUser("ana@centro.es", "$2a$10$hashActual");
            AuthenticatedUser identity = new AuthenticatedUser(
                    userId, "ana@centro.es", "Ana", UserRole.STUDENT, UUID.randomUUID());
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(identity, null, List.of()));
        }

        @AfterEach
        void cerrarSesion() {
            SecurityContextHolder.clearContext();
        }

        @Test
        @DisplayName("con la contraseña actual correcta guarda el hash de la nueva")
        void cambioCorrecto() {
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("actual-ok", "$2a$10$hashActual")).thenReturn(true);
            when(passwordEncoder.matches("nueva-clave-1", "$2a$10$hashActual")).thenReturn(false);
            when(passwordEncoder.encode("nueva-clave-1")).thenReturn("$2a$10$hashNuevo");

            when(jwtService.issueFor(user)).thenReturn("token-nuevo");

            AuthResponse answer = authService.changePassword(
                    new ChangePasswordRequest("actual-ok", "nueva-clave-1"), IP);

            assertThat(user.getPasswordHash()).isEqualTo("$2a$10$hashNuevo");
            verify(userRepository).save(user);
            // Las demás sesiones se cierran, y esta recibe un token nuevo para seguir abierta
            assertThat(user.getTokenVersion()).isEqualTo(1);
            assertThat(answer.token()).isEqualTo("token-nuevo");
        }

        @Test
        @DisplayName("con la actual incorrecta lanza 400 (no 401, que cerraría la sesión) y apunta el fallo")
        void actualIncorrecta() {
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("mala", "$2a$10$hashActual")).thenReturn(false);

            assertThatThrownBy(() -> authService.changePassword(
                    new ChangePasswordRequest("mala", "nueva-clave-1"), IP))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("La contraseña actual no es correcta");

            verify(loginAttemptService).recordFailure("ana@centro.es", IP);
            verify(userRepository, never()).save(user);
            assertThat(user.getPasswordHash()).isEqualTo("$2a$10$hashActual");
        }

        @Test
        @DisplayName("con la cuenta bloqueada por intentos fallidos no llega a mirar la contraseña")
        void cuentaBloqueada() {
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(loginAttemptService.isLocked("ana@centro.es")).thenReturn(true);
            when(loginAttemptService.minutesOfLockout()).thenReturn(15L);

            assertThatThrownBy(() -> authService.changePassword(
                    new ChangePasswordRequest("actual-ok", "nueva-clave-1"), IP))
                    .isInstanceOf(TooManyRequestsException.class);

            verifyNoInteractions(passwordEncoder);
        }

        @Test
        @DisplayName("rechaza una contraseña nueva igual a la actual")
        void nuevaIgualQueLaActual() {
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("misma-clave-1", "$2a$10$hashActual")).thenReturn(true);

            assertThatThrownBy(() -> authService.changePassword(
                    new ChangePasswordRequest("misma-clave-1", "misma-clave-1"), IP))
                    .isInstanceOf(BadRequestException.class);

            verify(userRepository, never()).save(user);
        }
    }
}
