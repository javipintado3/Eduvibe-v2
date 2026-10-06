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
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.eduvibe.dto.auth.AuthResponse;
import com.eduvibe.dto.auth.LoginRequest;
import com.eduvibe.exception.TooManyRequestsException;
import com.eduvibe.model.Organization;
import com.eduvibe.model.User;
import com.eduvibe.model.enums.UserRole;
import com.eduvibe.repository.UserRepository;
import com.eduvibe.security.JwtService;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String MENSAJE_CREDENCIALES_INVALIDAS = "Usuario y/o contraseña incorrectos";
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

    private AuthService authService;

    @BeforeEach
    void crearServicio() {
        authService = new AuthService(userRepository, passwordEncoder, jwtService, invitationService, loginAttemptService);
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
}
