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

    private static final String MENSAJE_CREDENCIALES_INVALIDAS =
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
        authService = new AuthService(userRepository, passwordEncoder, jwtService, invitationService, loginAttemptService, twoFactorService);
    }

    private User usuarioActivo(String email, String passwordHash) {
        User usuario = new User(new Organization("Centro", null), email, "Ana", UserRole.STUDENT);
        usuario.activarCon(passwordHash);
        return usuario;
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
                    .hasMessage(MENSAJE_CREDENCIALES_INVALIDAS);
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
            User usuario = usuarioActivo("ana@centro.es", "$2a$10$hash");
            when(userRepository.findByEmail("ana@centro.es")).thenReturn(Optional.of(usuario));
            when(passwordEncoder.matches("mala", "$2a$10$hash")).thenReturn(false);

            assertThatThrownBy(() -> authService.login(new LoginRequest("ana@centro.es", "mala"), IP))
                    .isInstanceOf(BadCredentialsException.class)
                    .hasMessage(MENSAJE_CREDENCIALES_INVALIDAS);
        }

        @Test
        @DisplayName("con cuenta pendiente de invitación lanza el mismo error aunque la contraseña sea correcta")
        void cuentaPendiente() {
            User usuario = new User(new Organization("Centro", null), "pendiente@centro.es", "Pendiente", UserRole.STUDENT);
            when(userRepository.findByEmail("pendiente@centro.es")).thenReturn(Optional.of(usuario));
            when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);

            assertThatThrownBy(() -> authService.login(new LoginRequest("pendiente@centro.es", "loQueSea"), IP))
                    .isInstanceOf(BadCredentialsException.class)
                    .hasMessage(MENSAJE_CREDENCIALES_INVALIDAS);
        }

        @Test
        @DisplayName("con cuenta desactivada y la contraseña correcta avisa de que está desactivada, sin contarlo como fallo")
        void cuentaDesactivadaConContrasenaCorrecta() {
            User usuario = usuarioActivo("ana@centro.es", "$2a$10$hash");
            usuario.setStatus(UserStatus.DISABLED);
            when(userRepository.findByEmail("ana@centro.es")).thenReturn(Optional.of(usuario));
            when(passwordEncoder.matches("correcta", "$2a$10$hash")).thenReturn(true);

            assertThatThrownBy(() -> authService.login(new LoginRequest("ana@centro.es", "correcta"), IP))
                    .isInstanceOf(AccountDisabledException.class)
                    .hasMessageContaining("desactivada");

            verify(loginAttemptService, never()).registrarFallo(anyString(), anyString());
        }

        @Test
        @DisplayName("con cuenta desactivada y contraseña incorrecta da el error genérico: no delata que la cuenta existe")
        void cuentaDesactivadaConContrasenaIncorrecta() {
            User usuario = usuarioActivo("ana@centro.es", "$2a$10$hash");
            usuario.setStatus(UserStatus.DISABLED);
            when(userRepository.findByEmail("ana@centro.es")).thenReturn(Optional.of(usuario));
            when(passwordEncoder.matches("mala", "$2a$10$hash")).thenReturn(false);

            assertThatThrownBy(() -> authService.login(new LoginRequest("ana@centro.es", "mala"), IP))
                    .isInstanceOf(BadCredentialsException.class)
                    .hasMessage(MENSAJE_CREDENCIALES_INVALIDAS);
            verify(loginAttemptService).registrarFallo("ana@centro.es", IP);
        }

        @Test
        @DisplayName("con el email bloqueado rechaza el intento sin llegar a mirar usuario ni contraseña")
        void emailBloqueado() {
            when(loginAttemptService.estaBloqueado("ana@centro.es")).thenReturn(true);
            when(loginAttemptService.minutosDeBloqueo()).thenReturn(15L);

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

            verify(loginAttemptService).registrarFallo("no-existe@centro.es", IP);
        }

        @Test
        @DisplayName("apunta el acceso correcto, que deja el contador de fallos a cero")
        void apuntaElExito() {
            User usuario = usuarioActivo("ana@centro.es", "$2a$10$hash");
            when(userRepository.findByEmail("ana@centro.es")).thenReturn(Optional.of(usuario));
            when(passwordEncoder.matches("correcta", "$2a$10$hash")).thenReturn(true);
            when(jwtService.emitirPara(usuario)).thenReturn("token-emitido");
            when(jwtService.caducidadDeUnTokenNuevo()).thenReturn(Instant.MAX);

            authService.login(new LoginRequest("ana@centro.es", "correcta"), IP);

            verify(loginAttemptService).registrarExito("ana@centro.es", IP);
            verify(loginAttemptService, never()).registrarFallo(anyString(), anyString());
        }

        private User usuarioConDosPasos() {
            User usuario = usuarioActivo("ana@centro.es", "$2a$10$hash");
            usuario.setTotpEnabled(true);
            when(userRepository.findByEmail("ana@centro.es")).thenReturn(Optional.of(usuario));
            when(passwordEncoder.matches("correcta", "$2a$10$hash")).thenReturn(true);
            return usuario;
        }

        @Test
        @DisplayName("con 2FA activado y sin código pide el código, sin contarlo como fallo")
        void dosPasosSinCodigo() {
            usuarioConDosPasos();

            assertThatThrownBy(() -> authService.login(new LoginRequest("ana@centro.es", "correcta"), IP))
                    .isInstanceOf(TwoFactorRequiredException.class);

            verify(loginAttemptService, never()).registrarFallo(anyString(), anyString());
            verify(loginAttemptService, never()).registrarExito(anyString(), anyString());
        }

        @Test
        @DisplayName("con 2FA activado y un código incorrecto lo trata como credenciales inválidas y apunta el fallo")
        void dosPasosCodigoIncorrecto() {
            User usuario = usuarioConDosPasos();
            when(twoFactorService.codigoValido(usuario, "000000")).thenReturn(false);

            assertThatThrownBy(() -> authService.login(
                    new LoginRequest("ana@centro.es", "correcta", "000000"), IP))
                    .isInstanceOf(BadCredentialsException.class);

            verify(loginAttemptService).registrarFallo("ana@centro.es", IP);
        }

        @Test
        @DisplayName("con 2FA activado y el código correcto inicia sesión")
        void dosPasosCodigoCorrecto() {
            User usuario = usuarioConDosPasos();
            when(twoFactorService.codigoValido(usuario, "123456")).thenReturn(true);
            when(jwtService.emitirPara(usuario)).thenReturn("token-emitido");
            when(jwtService.caducidadDeUnTokenNuevo()).thenReturn(Instant.MAX);

            AuthResponse respuesta = authService.login(
                    new LoginRequest("ana@centro.es", "correcta", "123456"), IP);

            assertThat(respuesta.token()).isEqualTo("token-emitido");
            verify(loginAttemptService).registrarExito("ana@centro.es", IP);
        }

        @Test
        @DisplayName("con credenciales correctas devuelve la sesión iniciada")
        void credencialesCorrectas() {
            User usuario = usuarioActivo("ana@centro.es", "$2a$10$hash");
            when(userRepository.findByEmail("ana@centro.es")).thenReturn(Optional.of(usuario));
            when(passwordEncoder.matches("correcta", "$2a$10$hash")).thenReturn(true);
            when(jwtService.emitirPara(usuario)).thenReturn("token-emitido");
            when(jwtService.caducidadDeUnTokenNuevo()).thenReturn(Instant.MAX);

            AuthResponse respuesta = authService.login(new LoginRequest("ana@centro.es", "correcta"), IP);

            assertThat(respuesta.token()).isEqualTo("token-emitido");
            assertThat(respuesta.user().email()).isEqualTo("ana@centro.es");
        }
    }

    @Nested
    @DisplayName("Cambiar contraseña")
    class CambiarPassword {

        private final UUID idUsuario = UUID.randomUUID();
        private User usuario;

        @BeforeEach
        void iniciarSesion() {
            usuario = usuarioActivo("ana@centro.es", "$2a$10$hashActual");
            AuthenticatedUser identidad = new AuthenticatedUser(
                    idUsuario, "ana@centro.es", "Ana", UserRole.STUDENT, UUID.randomUUID());
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(identidad, null, List.of()));
        }

        @AfterEach
        void cerrarSesion() {
            SecurityContextHolder.clearContext();
        }

        @Test
        @DisplayName("con la contraseña actual correcta guarda el hash de la nueva")
        void cambioCorrecto() {
            when(userRepository.findById(idUsuario)).thenReturn(Optional.of(usuario));
            when(passwordEncoder.matches("actual-ok", "$2a$10$hashActual")).thenReturn(true);
            when(passwordEncoder.matches("nueva-clave-1", "$2a$10$hashActual")).thenReturn(false);
            when(passwordEncoder.encode("nueva-clave-1")).thenReturn("$2a$10$hashNuevo");

            when(jwtService.emitirPara(usuario)).thenReturn("token-nuevo");

            AuthResponse respuesta = authService.cambiarPassword(
                    new ChangePasswordRequest("actual-ok", "nueva-clave-1"), IP);

            assertThat(usuario.getPasswordHash()).isEqualTo("$2a$10$hashNuevo");
            verify(userRepository).save(usuario);
            // Las demás sesiones se cierran, y esta recibe un token nuevo para seguir abierta
            assertThat(usuario.getTokenVersion()).isEqualTo(1);
            assertThat(respuesta.token()).isEqualTo("token-nuevo");
        }

        @Test
        @DisplayName("con la actual incorrecta lanza 400 (no 401, que cerraría la sesión) y apunta el fallo")
        void actualIncorrecta() {
            when(userRepository.findById(idUsuario)).thenReturn(Optional.of(usuario));
            when(passwordEncoder.matches("mala", "$2a$10$hashActual")).thenReturn(false);

            assertThatThrownBy(() -> authService.cambiarPassword(
                    new ChangePasswordRequest("mala", "nueva-clave-1"), IP))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("La contraseña actual no es correcta");

            verify(loginAttemptService).registrarFallo("ana@centro.es", IP);
            verify(userRepository, never()).save(usuario);
            assertThat(usuario.getPasswordHash()).isEqualTo("$2a$10$hashActual");
        }

        @Test
        @DisplayName("con la cuenta bloqueada por intentos fallidos no llega a mirar la contraseña")
        void cuentaBloqueada() {
            when(userRepository.findById(idUsuario)).thenReturn(Optional.of(usuario));
            when(loginAttemptService.estaBloqueado("ana@centro.es")).thenReturn(true);
            when(loginAttemptService.minutosDeBloqueo()).thenReturn(15L);

            assertThatThrownBy(() -> authService.cambiarPassword(
                    new ChangePasswordRequest("actual-ok", "nueva-clave-1"), IP))
                    .isInstanceOf(TooManyRequestsException.class);

            verifyNoInteractions(passwordEncoder);
        }

        @Test
        @DisplayName("rechaza una contraseña nueva igual a la actual")
        void nuevaIgualQueLaActual() {
            when(userRepository.findById(idUsuario)).thenReturn(Optional.of(usuario));
            when(passwordEncoder.matches("misma-clave-1", "$2a$10$hashActual")).thenReturn(true);

            assertThatThrownBy(() -> authService.cambiarPassword(
                    new ChangePasswordRequest("misma-clave-1", "misma-clave-1"), IP))
                    .isInstanceOf(BadRequestException.class);

            verify(userRepository, never()).save(usuario);
        }
    }
}
