package com.eduvibe.service;

import java.time.Duration;
import java.time.Instant;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.eduvibe.config.AppProperties;
import com.eduvibe.exception.BadRequestException;
import com.eduvibe.model.PasswordReset;
import com.eduvibe.model.User;
import com.eduvibe.repository.PasswordResetRepository;
import com.eduvibe.repository.UserRepository;
import com.eduvibe.util.Tokens;

import lombok.RequiredArgsConstructor;

/**
 * "Olvidé mi contraseña".
 *
 * Solo opera sobre cuentas que ya existen y están activas: nunca crea cuentas,
 * para que no se convierta en una puerta trasera de registro. Una cuenta
 * pendiente se activa con su invitación y una desactivada no puede entrar,
 * así que a ninguna de las dos se le envía nada.
 */
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    /**
     * Mientras no pase este tiempo desde la última petición de un usuario, se
     * ignoran las nuevas: evita que alguien llene de correos la bandeja de otra
     * persona pulsando el botón en bucle.
     */
    private static final Duration WAIT_BETWEEN_REQUESTS = Duration.ofMinutes(1);

    private final PasswordResetRepository passwordResetRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final MailService mailService;
    private final AppProperties properties;

    /**
     * Emite un enlace para el email indicado, si corresponde a una cuenta activa.
     *
     * No devuelve nada ni lanza error aunque el email no exista: el controlador
     * responde siempre lo mismo, porque distinguir los casos permitiría
     * averiguar qué direcciones están dadas de alta.
     */
    @Transactional
    public void request(String email) {
        userRepository.findByEmail(User.normalizeEmail(email))
                .filter(User::canStartSession)
                .filter(user -> !passwordResetRepository.existsByUserIdAndCreatedAtAfter(
                        user.getId(), Instant.now().minus(WAIT_BETWEEN_REQUESTS)))
                .ifPresent(this::issueFor);
    }

    /**
     * Consume el enlace: establece la contraseña nueva e invalida cualquier otro
     * enlace pendiente de esa persona.
     */
    @Transactional
    public void reset(String token, String password) {
        PasswordReset recovery = passwordResetRepository.findByTokenHash(Tokens.hash(token))
                .filter(PasswordReset::isUsable)
                // Inexistente, ya usado y caducado dan el mismo error a propósito:
                // distinguirlos permitiría averiguar qué tokens han existido
                .orElseThrow(() -> new BadRequestException(
                        "El enlace no es válido o ha caducado. Solicita uno nuevo."));

        User user = recovery.getUser();
        // Entre que se pidió el enlace y se usa, la cuenta pudo desactivarse
        if (!user.canStartSession()) {
            throw new BadRequestException("El enlace no es válido o ha caducado. Solicita uno nuevo.");
        }

        user.changePassword(passwordEncoder.encode(password));
        // Si alguien más tenía la cuenta abierta (el motivo habitual para recuperarla), se le cierra
        user.invalidateSessions();
        userRepository.save(user);

        passwordResetRepository.invalidatePendingFor(user.getId());
    }

    private void issueFor(User user) {
        passwordResetRepository.invalidatePendingFor(user.getId());

        String token = Tokens.generate();
        Instant expiry = Instant.now()
                .plus(Duration.ofMinutes(properties.passwordReset().expirationMinutes()));

        passwordResetRepository.save(new PasswordReset(user, Tokens.hash(token), expiry));

        mailService.sendReset(user, buildLink(token));
    }

    private String buildLink(String token) {
        String base = properties.frontendUrl();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/restablecer/" + token;
    }
}
