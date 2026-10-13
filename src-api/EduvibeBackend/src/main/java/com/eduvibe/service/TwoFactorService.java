package com.eduvibe.service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.eduvibe.dto.auth.TwoFactorSetupResponse;
import com.eduvibe.exception.BadRequestException;
import com.eduvibe.exception.ConflictException;
import com.eduvibe.exception.NotFoundException;
import com.eduvibe.exception.TooManyRequestsException;
import com.eduvibe.model.TotpRecoveryCode;
import com.eduvibe.model.User;
import com.eduvibe.repository.TotpRecoveryCodeRepository;
import com.eduvibe.repository.UserRepository;
import com.eduvibe.util.Tokens;
import com.eduvibe.util.Totp;

import lombok.RequiredArgsConstructor;

/**
 * Verificación en dos pasos con una app de autenticación (TOTP).
 *
 * Activarla es un proceso en dos tiempos a propósito: primero se genera el
 * secreto y se enseña el QR, y solo cuando la persona demuestra con un código
 * que su app lo ha leído bien se da por activada. Si se activara al generar el
 * secreto, quien cerrara la pantalla a medias se quedaría sin poder entrar.
 *
 * Por eso mismo hay códigos de recuperación: si se pierde el móvil, son la
 * única salida sin pasar por la administración.
 */
@Service
@RequiredArgsConstructor
public class TwoFactorService {

    /** Nombre que la app de autenticación muestra junto a la cuenta. */
    private static final String ISSUER = "EduVibe";

    private static final int RECOVERY_CODES = 8;
    private static final int CODE_LENGTH = 10;

    /** Sin 0/o ni 1/l/i, que se confunden al copiarlos a mano. */
    private static final String ALPHABET_RECOVERY = "abcdefghjkmnpqrstuvwxyz23456789";

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final TotpRecoveryCodeRepository recoveryCodeRepository;
    private final PasswordEncoder passwordEncoder;
    private final ProtectedDemoAccounts protectedDemoAccounts;
    private final LoginAttemptService loginAttemptService;

    /** Primer paso: genera el secreto y devuelve lo necesario para pintar el QR. */
    @Transactional
    public TwoFactorSetupResponse startConfiguration(UUID userId) {
        User user = load(userId);
        protectedDemoAccounts.requireNotProtected(user);
        if (user.isTotpEnabled()) {
            throw new ConflictException("La verificación en dos pasos ya está activada");
        }

        String secret = Totp.generateSecret();
        user.setTotpSecret(secret);
        userRepository.save(user);

        return new TwoFactorSetupResponse(secret, Totp.uri(ISSUER, user.getEmail(), secret));
    }

    /**
     * Segundo paso: comprueba el código y activa. Devuelve los códigos de
     * recuperación en claro; es la única vez que se pueden ver.
     */
    @Transactional
    public List<String> enable(UUID userId, String code) {
        User user = load(userId);
        protectedDemoAccounts.requireNotProtected(user);
        if (user.isTotpEnabled()) {
            throw new ConflictException("La verificación en dos pasos ya está activada");
        }
        if (user.getTotpSecret() == null) {
            throw new BadRequestException("Primero hay que empezar la configuración");
        }
        if (!Totp.verify(user.getTotpSecret(), code, Instant.now())) {
            throw new BadRequestException("El código no es correcto. Comprueba la hora de tu móvil e inténtalo de nuevo.");
        }

        user.setTotpEnabled(true);
        userRepository.save(user);

        return generateRecoveryCodes(user);
    }

    /**
     * Desactiva el 2FA. Pide la contraseña y un código, para que quien se
     * encuentre una sesión abierta no pueda quitar la protección. Un fallo
     * cuenta para el límite de intentos del login, igual que en el cambio de
     * contraseña.
     */
    @Transactional
    public void disable(UUID userId, String password, String code, String ip) {
        User user = load(userId);
        if (!user.isTotpEnabled()) {
            throw new BadRequestException("La verificación en dos pasos no está activada");
        }

        if (loginAttemptService.isLocked(user.getEmail())) {
            throw new TooManyRequestsException("Demasiados intentos fallidos. Vuelve a intentarlo en "
                    + loginAttemptService.minutesOfLockout() + " minutos");
        }

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            loginAttemptService.recordFailure(user.getEmail(), ip);
            throw new BadRequestException("La contraseña no es correcta");
        }
        if (!validCode(user, code)) {
            loginAttemptService.recordFailure(user.getEmail(), ip);
            throw new BadRequestException("El código no es correcto");
        }

        user.setTotpEnabled(false);
        user.setTotpSecret(null);
        userRepository.save(user);
        recoveryCodeRepository.deleteByUser(user.getId());
    }

    /**
     * ¿Vale este código para entrar? Acepta el de 6 dígitos de la app o uno de
     * recuperación, que se gasta al usarlo.
     */
    @Transactional
    public boolean validCode(User user, String code) {
        if (code == null || code.isBlank()) {
            return false;
        }
        String clean = code.trim();

        return Totp.verify(user.getTotpSecret(), clean, Instant.now())
                || consumeRecoveryCode(user, clean);
    }

    private boolean consumeRecoveryCode(User user, String code) {
        return recoveryCodeRepository
                .findByUserIdAndCodeHashAndUsedAtIsNull(user.getId(), Tokens.hash(normalize(code)))
                .map(recovery -> {
                    recovery.markAsUsed();
                    recoveryCodeRepository.save(recovery);
                    return true;
                })
                .orElse(false);
    }

    /** Un juego nuevo sustituye al anterior: los viejos dejan de valer. */
    private List<String> generateRecoveryCodes(User user) {
        recoveryCodeRepository.deleteByUser(user.getId());

        List<String> codes = new ArrayList<>();
        for (int i = 0; i < RECOVERY_CODES; i++) {
            String code = generateCode();
            recoveryCodeRepository.save(new TotpRecoveryCode(user, Tokens.hash(normalize(code))));
            codes.add(code);
        }
        return codes;
    }

    /** Con forma "abcde-fghij": más fácil de copiar a mano que diez letras seguidas. */
    private String generateCode() {
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < CODE_LENGTH; i++) {
            if (i == CODE_LENGTH / 2) {
                code.append('-');
            }
            code.append(ALPHABET_RECOVERY.charAt(RANDOM.nextInt(ALPHABET_RECOVERY.length())));
        }
        return code.toString();
    }

    /** Se guarda y se compara sin guion, mayúsculas ni espacios, para perdonar cómo se teclee. */
    private String normalize(String code) {
        return code.replace("-", "").replace(" ", "").toLowerCase();
    }

    private User load(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> NotFoundException.of("Usuario", userId));
    }
}
