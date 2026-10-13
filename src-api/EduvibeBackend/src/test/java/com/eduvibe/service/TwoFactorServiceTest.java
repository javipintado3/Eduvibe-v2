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

    private TwoFactorService service;

    private final UUID userId = UUID.randomUUID();
    private User user;

    @BeforeEach
    void preparar() {
        service = new TwoFactorService(userRepository, recoveryCodeRepository, passwordEncoder, new ProtectedDemoAccounts(""), loginAttemptService);
        user = new User(new Organization("Centro", null), "ana@centro.es", "Ana", UserRole.TEACHER);
        user.enableWith("$2a$10$hash");
    }

    private void userExists() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    }

    @Nested
    @DisplayName("Configurar")
    class Configure {

        @Test
        @DisplayName("genera un secreto y lo guarda sin activar todavía el 2FA")
        void iniciaSinActivar() {
            userExists();

            TwoFactorSetupResponse answer = service.startConfiguration(userId);

            assertThat(user.getTotpSecret()).isEqualTo(answer.secret());
            assertThat(user.isTotpEnabled()).isFalse();
            assertThat(answer.otpauthUri()).contains(answer.secret()).startsWith("otpauth://totp/EduVibe:");
        }

        @Test
        @DisplayName("si ya está activado no deja volver a empezar")
        void yaActivado() {
            userExists();
            user.setTotpEnabled(true);

            assertThatThrownBy(() -> service.startConfiguration(userId))
                    .isInstanceOf(ConflictException.class);
        }

        @Test
        @DisplayName("con un código correcto activa el 2FA y entrega 8 códigos de recuperación, guardando solo su hash")
        void activaConCodigoCorrecto() {
            userExists();
            String secret = Totp.generateSecret();
            user.setTotpSecret(secret);

            List<String> codes = service.enable(userId, Totp.codeIn(secret, Instant.now()));

            assertThat(user.isTotpEnabled()).isTrue();
            assertThat(codes).hasSize(8).doesNotHaveDuplicates().allMatch(c -> c.matches("[a-z2-9]{5}-[a-z2-9]{5}"));

            ArgumentCaptor<TotpRecoveryCode> saved = ArgumentCaptor.forClass(TotpRecoveryCode.class);
            verify(recoveryCodeRepository, times(8)).save(saved.capture());
            assertThat(saved.getAllValues())
                    .extracting(TotpRecoveryCode::getCodeHash)
                    .doesNotContainAnyElementsOf(codes)
                    .contains(Tokens.hash(codes.get(0).replace("-", "")));
        }

        @Test
        @DisplayName("con un código incorrecto no activa nada")
        void noActivaConCodigoIncorrecto() {
            userExists();
            String secret = Totp.generateSecret();
            user.setTotpSecret(secret);
            String correct = Totp.codeIn(secret, Instant.now());
            String incorrect = correct.equals("000000") ? "000001" : "000000";

            assertThatThrownBy(() -> service.enable(userId, incorrect))
                    .isInstanceOf(BadRequestException.class);

            assertThat(user.isTotpEnabled()).isFalse();
            verify(recoveryCodeRepository, never()).save(any());
        }

        @Test
        @DisplayName("sin haber empezado la configuración no se puede activar")
        void sinEmpezar() {
            userExists();

            assertThatThrownBy(() -> service.enable(userId, "123456"))
                    .isInstanceOf(BadRequestException.class);
        }
    }

    @Nested
    @DisplayName("Desactivar")
    class Disable {

        private String secret;

        @BeforeEach
        void conDosPasosActivado() {
            secret = Totp.generateSecret();
            user.setTotpSecret(secret);
            user.setTotpEnabled(true);
        }

        @Test
        @DisplayName("con contraseña y código correctos lo desactiva y borra el secreto y los códigos de recuperación")
        void desactiva() {
            userExists();
            when(passwordEncoder.matches("clave", "$2a$10$hash")).thenReturn(true);

            service.disable(userId, "clave", Totp.codeIn(secret, Instant.now()), IP);

            assertThat(user.isTotpEnabled()).isFalse();
            assertThat(user.getTotpSecret()).isNull();
            verify(recoveryCodeRepository).deleteByUser(user.getId());
        }

        @Test
        @DisplayName("con la contraseña incorrecta no desactiva y apunta el fallo")
        void contrasenaIncorrecta() {
            userExists();
            when(passwordEncoder.matches("mala", "$2a$10$hash")).thenReturn(false);

            assertThatThrownBy(() -> service.disable(userId, "mala", "123456", IP))
                    .isInstanceOf(BadRequestException.class);

            assertThat(user.isTotpEnabled()).isTrue();
            verify(loginAttemptService).recordFailure("ana@centro.es", IP);
        }

        @Test
        @DisplayName("con el código incorrecto no desactiva y apunta el fallo")
        void codigoIncorrecto() {
            userExists();
            when(passwordEncoder.matches("clave", "$2a$10$hash")).thenReturn(true);
            String incorrect = Totp.codeIn(secret, Instant.now()).equals("000000") ? "000001" : "000000";

            assertThatThrownBy(() -> service.disable(userId, "clave", incorrect, IP))
                    .isInstanceOf(BadRequestException.class);

            assertThat(user.isTotpEnabled()).isTrue();
            verify(loginAttemptService).recordFailure("ana@centro.es", IP);
        }

        @Test
        @DisplayName("con la cuenta bloqueada por intentos fallidos ni mira la contraseña")
        void bloqueada() {
            userExists();
            when(loginAttemptService.isLocked("ana@centro.es")).thenReturn(true);
            when(loginAttemptService.minutesOfLockout()).thenReturn(15L);

            assertThatThrownBy(() -> service.disable(userId, "clave", "123456", IP))
                    .isInstanceOf(TooManyRequestsException.class);

            verify(passwordEncoder, never()).matches(anyString(), anyString());
        }

        @Test
        @DisplayName("si no estaba activado lo dice")
        void noActivado() {
            userExists();
            user.setTotpEnabled(false);

            assertThatThrownBy(() -> service.disable(userId, "clave", "123456", IP))
                    .isInstanceOf(BadRequestException.class);
        }
    }

    @Nested
    @DisplayName("Comprobar un código al entrar")
    class CheckCode {

        private String secret;

        @BeforeEach
        void conDosPasosActivado() {
            secret = Totp.generateSecret();
            user.setTotpSecret(secret);
            user.setTotpEnabled(true);
        }

        @Test
        @DisplayName("acepta el código de la app")
        void codigoDeLaApp() {
            assertThat(service.validCode(user, Totp.codeIn(secret, Instant.now()))).isTrue();
        }

        @Test
        @DisplayName("acepta un código de recuperación sin gastar y lo marca como usado")
        void codigoDeRecuperacion() {
            TotpRecoveryCode saved = new TotpRecoveryCode(user, Tokens.hash("abcdefghjk"));
            when(recoveryCodeRepository.findByUserIdAndCodeHashAndUsedAtIsNull(
                    user.getId(), Tokens.hash("abcdefghjk"))).thenReturn(Optional.of(saved));

            // Con guion y en mayúsculas: se perdona cómo se teclee
            assertThat(service.validCode(user, "ABCDE-FGHJK")).isTrue();

            assertThat(saved.getUsedAt()).isNotNull();
            verify(recoveryCodeRepository).save(saved);
        }

        @Test
        @DisplayName("rechaza un código de recuperación ya gastado o inventado")
        void codigoDeRecuperacionInvalido() {
            when(recoveryCodeRepository.findByUserIdAndCodeHashAndUsedAtIsNull(any(), anyString()))
                    .thenReturn(Optional.empty());

            assertThat(service.validCode(user, "zzzzz-zzzzz")).isFalse();
        }

        @Test
        @DisplayName("rechaza un código vacío sin consultar nada")
        void codigoVacio() {
            assertThat(service.validCode(user, " ")).isFalse();
            assertThat(service.validCode(user, null)).isFalse();

            verify(recoveryCodeRepository, never()).findByUserIdAndCodeHashAndUsedAtIsNull(any(), anyString());
        }
    }
}
