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
    private static final Duration ESPERA_ENTRE_PETICIONES = Duration.ofMinutes(1);

    private final PasswordResetRepository passwordResetRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final MailService mailService;
    private final AppProperties propiedades;

    /**
     * Emite un enlace para el email indicado, si corresponde a una cuenta activa.
     *
     * No devuelve nada ni lanza error aunque el email no exista: el controlador
     * responde siempre lo mismo, porque distinguir los casos permitiría
     * averiguar qué direcciones están dadas de alta.
     */
    @Transactional
    public void solicitar(String email) {
        userRepository.findByEmail(User.normalizarEmail(email))
                .filter(User::puedeIniciarSesion)
                .filter(usuario -> !passwordResetRepository.existsByUserIdAndCreatedAtAfter(
                        usuario.getId(), Instant.now().minus(ESPERA_ENTRE_PETICIONES)))
                .ifPresent(this::emitirPara);
    }

    /**
     * Consume el enlace: establece la contraseña nueva e invalida cualquier otro
     * enlace pendiente de esa persona.
     */
    @Transactional
    public void restablecer(String token, String password) {
        PasswordReset recuperacion = passwordResetRepository.findByTokenHash(Tokens.hashear(token))
                .filter(PasswordReset::esUtilizable)
                // Inexistente, ya usado y caducado dan el mismo error a propósito:
                // distinguirlos permitiría averiguar qué tokens han existido
                .orElseThrow(() -> new BadRequestException(
                        "El enlace no es válido o ha caducado. Solicita uno nuevo."));

        User usuario = recuperacion.getUser();
        // Entre que se pidió el enlace y se usa, la cuenta pudo desactivarse
        if (!usuario.puedeIniciarSesion()) {
            throw new BadRequestException("El enlace no es válido o ha caducado. Solicita uno nuevo.");
        }

        usuario.cambiarPassword(passwordEncoder.encode(password));
        userRepository.save(usuario);

        passwordResetRepository.invalidarPendientesDe(usuario.getId());
    }

    private void emitirPara(User usuario) {
        passwordResetRepository.invalidarPendientesDe(usuario.getId());

        String token = Tokens.generar();
        Instant caducidad = Instant.now()
                .plus(Duration.ofMinutes(propiedades.passwordReset().expirationMinutes()));

        passwordResetRepository.save(new PasswordReset(usuario, Tokens.hashear(token), caducidad));

        mailService.enviarRestablecimiento(usuario, construirEnlace(token));
    }

    private String construirEnlace(String token) {
        String base = propiedades.frontendUrl();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/restablecer/" + token;
    }
}
