package com.eduvibe.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.eduvibe.dto.auth.TwoFactorSetupResponse;
import com.eduvibe.exception.BadRequestException;
import com.eduvibe.exception.ConflictException;
import com.eduvibe.exception.TooManyRequestsException;
import com.eduvibe.model.Organization;
import com.eduvibe.model.TotpRecoveryCode;
import com.eduvibe.model.User;
import com.eduvibe.model.enums.UserRole;
import com.eduvibe.repository.TotpRecoveryCodeRepository;
import com.eduvibe.repository.UserRepository;
import com.eduvibe.util.Tokens;
import com.eduvibe.util.Totp;

@ExtendWith(MockitoExtension.class)
class TwoFactorServiceTest {

    private static final String IP = "127.0.0.1";

    @Mock
    private UserRepository userRepository;

    @Mock
    private TotpRecoveryCodeRepository recoveryCodeRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private LoginAttemptService loginAttemptService;

    private TwoFactorService servicio;

    private final UUID idUsuario = UUID.randomUUID();
    private User usuario;

    @BeforeEach
    void preparar() {
        servicio = new TwoFactorService(userRepository, recoveryCodeRepository, passwordEncoder, loginAttemptService);
        usuario = new User(new Organization("Centro", null), "ana@centro.es", "Ana", UserRole.TEACHER);
        usuario.activarCon("$2a$10$hash");
    }

    private void usuarioExiste() {
        when(userRepository.findById(idUsuario)).thenReturn(Optional.of(usuario));
    }

    @Nested
    @DisplayName("Configurar")
    class Configurar {

        @Test
        @DisplayName("genera un secreto y lo guarda sin activar todavía el 2FA")
        void iniciaSinActivar() {
            usuarioExiste();

            TwoFactorSetupResponse respuesta = servicio.iniciarConfiguracion(idUsuario);

            assertThat(usuario.getTotpSecret()).isEqualTo(respuesta.secret());
            assertThat(usuario.isTotpEnabled()).isFalse();
            assertThat(respuesta.otpauthUri()).contains(respuesta.secret()).startsWith("otpauth://totp/EduVibe:");
        }

        @Test
        @DisplayName("si ya está activado no deja volver a empezar")
        void yaActivado() {
            usuarioExiste();
            usuario.setTotpEnabled(true);

            assertThatThrownBy(() -> servicio.iniciarConfiguracion(idUsuario))
                    .isInstanceOf(ConflictException.class);
        }

        @Test
        @DisplayName("con un código correcto activa el 2FA y entrega 8 códigos de recuperación, guardando solo su hash")
        void activaConCodigoCorrecto() {
            usuarioExiste();
            String secreto = Totp.generarSecreto();
            usuario.setTotpSecret(secreto);

            List<String> codigos = servicio.activar(idUsuario, Totp.codigoEn(secreto, Instant.now()));

            assertThat(usuario.isTotpEnabled()).isTrue();
            assertThat(codigos).hasSize(8).doesNotHaveDuplicates().allMatch(c -> c.matches("[a-z2-9]{5}-[a-z2-9]{5}"));

            ArgumentCaptor<TotpRecoveryCode> guardados = ArgumentCaptor.forClass(TotpRecoveryCode.class);
            verify(recoveryCodeRepository, times(8)).save(guardados.capture());
            assertThat(guardados.getAllValues())
                    .extracting(TotpRecoveryCode::getCodeHash)
                    .doesNotContainAnyElementsOf(codigos)
                    .contains(Tokens.hashear(codigos.get(0).replace("-", "")));
        }

        @Test
        @DisplayName("con un código incorrecto no activa nada")
        void noActivaConCodigoIncorrecto() {
            usuarioExiste();
            String secreto = Totp.generarSecreto();
            usuario.setTotpSecret(secreto);
            String correcto = Totp.codigoEn(secreto, Instant.now());
            String incorrecto = correcto.equals("000000") ? "000001" : "000000";

            assertThatThrownBy(() -> servicio.activar(idUsuario, incorrecto))
                    .isInstanceOf(BadRequestException.class);

            assertThat(usuario.isTotpEnabled()).isFalse();
            verify(recoveryCodeRepository, never()).save(any());
        }

        @Test
        @DisplayName("sin haber empezado la configuración no se puede activar")
        void sinEmpezar() {
            usuarioExiste();

            assertThatThrownBy(() -> servicio.activar(idUsuario, "123456"))
                    .isInstanceOf(BadRequestException.class);
        }
    }

    @Nested
    @DisplayName("Desactivar")
    class Desactivar {

        private String secreto;

        @BeforeEach
        void conDosPasosActivado() {
            secreto = Totp.generarSecreto();
            usuario.setTotpSecret(secreto);
            usuario.setTotpEnabled(true);
        }

        @Test
        @DisplayName("con contraseña y código correctos lo desactiva y borra el secreto y los códigos de recuperación")
        void desactiva() {
            usuarioExiste();
            when(passwordEncoder.matches("clave", "$2a$10$hash")).thenReturn(true);

            servicio.desactivar(idUsuario, "clave", Totp.codigoEn(secreto, Instant.now()), IP);

            assertThat(usuario.isTotpEnabled()).isFalse();
            assertThat(usuario.getTotpSecret()).isNull();
            verify(recoveryCodeRepository).borrarDe(usuario.getId());
        }

        @Test
        @DisplayName("con la contraseña incorrecta no desactiva y apunta el fallo")
        void contrasenaIncorrecta() {
            usuarioExiste();
            when(passwordEncoder.matches("mala", "$2a$10$hash")).thenReturn(false);

            assertThatThrownBy(() -> servicio.desactivar(idUsuario, "mala", "123456", IP))
                    .isInstanceOf(BadRequestException.class);

            assertThat(usuario.isTotpEnabled()).isTrue();
            verify(loginAttemptService).registrarFallo("ana@centro.es", IP);
        }

        @Test
        @DisplayName("con el código incorrecto no desactiva y apunta el fallo")
        void codigoIncorrecto() {
            usuarioExiste();
            when(passwordEncoder.matches("clave", "$2a$10$hash")).thenReturn(true);
            String incorrecto = Totp.codigoEn(secreto, Instant.now()).equals("000000") ? "000001" : "000000";

            assertThatThrownBy(() -> servicio.desactivar(idUsuario, "clave", incorrecto, IP))
                    .isInstanceOf(BadRequestException.class);

            assertThat(usuario.isTotpEnabled()).isTrue();
            verify(loginAttemptService).registrarFallo("ana@centro.es", IP);
        }

        @Test
        @DisplayName("con la cuenta bloqueada por intentos fallidos ni mira la contraseña")
        void bloqueada() {
            usuarioExiste();
            when(loginAttemptService.estaBloqueado("ana@centro.es")).thenReturn(true);
            when(loginAttemptService.minutosDeBloqueo()).thenReturn(15L);

            assertThatThrownBy(() -> servicio.desactivar(idUsuario, "clave", "123456", IP))
                    .isInstanceOf(TooManyRequestsException.class);

            verify(passwordEncoder, never()).matches(anyString(), anyString());
        }

        @Test
        @DisplayName("si no estaba activado lo dice")
        void noActivado() {
            usuarioExiste();
            usuario.setTotpEnabled(false);

            assertThatThrownBy(() -> servicio.desactivar(idUsuario, "clave", "123456", IP))
                    .isInstanceOf(BadRequestException.class);
        }
    }

    @Nested
    @DisplayName("Comprobar un código al entrar")
    class ComprobarCodigo {

        private String secreto;

        @BeforeEach
        void conDosPasosActivado() {
            secreto = Totp.generarSecreto();
            usuario.setTotpSecret(secreto);
            usuario.setTotpEnabled(true);
        }

        @Test
        @DisplayName("acepta el código de la app")
        void codigoDeLaApp() {
            assertThat(servicio.codigoValido(usuario, Totp.codigoEn(secreto, Instant.now()))).isTrue();
        }

        @Test
        @DisplayName("acepta un código de recuperación sin gastar y lo marca como usado")
        void codigoDeRecuperacion() {
            TotpRecoveryCode guardado = new TotpRecoveryCode(usuario, Tokens.hashear("abcdefghjk"));
            when(recoveryCodeRepository.findByUserIdAndCodeHashAndUsedAtIsNull(
                    usuario.getId(), Tokens.hashear("abcdefghjk"))).thenReturn(Optional.of(guardado));

            // Con guion y en mayúsculas: se perdona cómo se teclee
            assertThat(servicio.codigoValido(usuario, "ABCDE-FGHJK")).isTrue();

            assertThat(guardado.getUsedAt()).isNotNull();
            verify(recoveryCodeRepository).save(guardado);
        }

        @Test
        @DisplayName("rechaza un código de recuperación ya gastado o inventado")
        void codigoDeRecuperacionInvalido() {
            when(recoveryCodeRepository.findByUserIdAndCodeHashAndUsedAtIsNull(any(), anyString()))
                    .thenReturn(Optional.empty());

            assertThat(servicio.codigoValido(usuario, "zzzzz-zzzzz")).isFalse();
        }

        @Test
        @DisplayName("rechaza un código vacío sin consultar nada")
        void codigoVacio() {
            assertThat(servicio.codigoValido(usuario, " ")).isFalse();
            assertThat(servicio.codigoValido(usuario, null)).isFalse();

            verify(recoveryCodeRepository, never()).findByUserIdAndCodeHashAndUsedAtIsNull(any(), anyString());
        }
    }
}
