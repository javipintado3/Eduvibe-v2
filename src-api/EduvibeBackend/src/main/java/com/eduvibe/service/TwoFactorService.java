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
    private static final String EMISOR = "EduVibe";

    private static final int CODIGOS_DE_RECUPERACION = 8;
    private static final int LONGITUD_DEL_CODIGO = 10;

    /** Sin 0/o ni 1/l/i, que se confunden al copiarlos a mano. */
    private static final String ALFABETO_RECUPERACION = "abcdefghjkmnpqrstuvwxyz23456789";

    private static final SecureRandom ALEATORIO = new SecureRandom();

    private final UserRepository userRepository;
    private final TotpRecoveryCodeRepository recoveryCodeRepository;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptService loginAttemptService;

    /** Primer paso: genera el secreto y devuelve lo necesario para pintar el QR. */
    @Transactional
    public TwoFactorSetupResponse iniciarConfiguracion(UUID usuarioId) {
        User usuario = cargar(usuarioId);
        if (usuario.isTotpEnabled()) {
            throw new ConflictException("La verificación en dos pasos ya está activada");
        }

        String secreto = Totp.generarSecreto();
        usuario.setTotpSecret(secreto);
        userRepository.save(usuario);

        return new TwoFactorSetupResponse(secreto, Totp.uri(EMISOR, usuario.getEmail(), secreto));
    }

    /**
     * Segundo paso: comprueba el código y activa. Devuelve los códigos de
     * recuperación en claro; es la única vez que se pueden ver.
     */
    @Transactional
    public List<String> activar(UUID usuarioId, String codigo) {
        User usuario = cargar(usuarioId);
        if (usuario.isTotpEnabled()) {
            throw new ConflictException("La verificación en dos pasos ya está activada");
        }
        if (usuario.getTotpSecret() == null) {
            throw new BadRequestException("Primero hay que empezar la configuración");
        }
        if (!Totp.verificar(usuario.getTotpSecret(), codigo, Instant.now())) {
            throw new BadRequestException("El código no es correcto. Comprueba la hora de tu móvil e inténtalo de nuevo.");
        }

        usuario.setTotpEnabled(true);
        userRepository.save(usuario);

        return generarCodigosDeRecuperacion(usuario);
    }

    /**
     * Desactiva el 2FA. Pide la contraseña y un código, para que quien se
     * encuentre una sesión abierta no pueda quitar la protección. Un fallo
     * cuenta para el límite de intentos del login, igual que en el cambio de
     * contraseña.
     */
    @Transactional
    public void desactivar(UUID usuarioId, String password, String codigo, String ip) {
        User usuario = cargar(usuarioId);
        if (!usuario.isTotpEnabled()) {
            throw new BadRequestException("La verificación en dos pasos no está activada");
        }

        if (loginAttemptService.estaBloqueado(usuario.getEmail())) {
            throw new TooManyRequestsException("Demasiados intentos fallidos. Vuelve a intentarlo en "
                    + loginAttemptService.minutosDeBloqueo() + " minutos");
        }

        if (!passwordEncoder.matches(password, usuario.getPasswordHash())) {
            loginAttemptService.registrarFallo(usuario.getEmail(), ip);
            throw new BadRequestException("La contraseña no es correcta");
        }
        if (!codigoValido(usuario, codigo)) {
            loginAttemptService.registrarFallo(usuario.getEmail(), ip);
            throw new BadRequestException("El código no es correcto");
        }

        usuario.setTotpEnabled(false);
        usuario.setTotpSecret(null);
        userRepository.save(usuario);
        recoveryCodeRepository.borrarDe(usuario.getId());
    }

    /**
     * ¿Vale este código para entrar? Acepta el de 6 dígitos de la app o uno de
     * recuperación, que se gasta al usarlo.
     */
    @Transactional
    public boolean codigoValido(User usuario, String codigo) {
        if (codigo == null || codigo.isBlank()) {
            return false;
        }
        String limpio = codigo.trim();

        return Totp.verificar(usuario.getTotpSecret(), limpio, Instant.now())
                || consumirCodigoDeRecuperacion(usuario, limpio);
    }

    private boolean consumirCodigoDeRecuperacion(User usuario, String codigo) {
        return recoveryCodeRepository
                .findByUserIdAndCodeHashAndUsedAtIsNull(usuario.getId(), Tokens.hashear(normalizar(codigo)))
                .map(recuperacion -> {
                    recuperacion.marcarComoUsado();
                    recoveryCodeRepository.save(recuperacion);
                    return true;
                })
                .orElse(false);
    }

    /** Un juego nuevo sustituye al anterior: los viejos dejan de valer. */
    private List<String> generarCodigosDeRecuperacion(User usuario) {
        recoveryCodeRepository.borrarDe(usuario.getId());

        List<String> codigos = new ArrayList<>();
        for (int i = 0; i < CODIGOS_DE_RECUPERACION; i++) {
            String codigo = generarCodigo();
            recoveryCodeRepository.save(new TotpRecoveryCode(usuario, Tokens.hashear(normalizar(codigo))));
            codigos.add(codigo);
        }
        return codigos;
    }

    /** Con forma "abcde-fghij": más fácil de copiar a mano que diez letras seguidas. */
    private String generarCodigo() {
        StringBuilder codigo = new StringBuilder();
        for (int i = 0; i < LONGITUD_DEL_CODIGO; i++) {
            if (i == LONGITUD_DEL_CODIGO / 2) {
                codigo.append('-');
            }
            codigo.append(ALFABETO_RECUPERACION.charAt(ALEATORIO.nextInt(ALFABETO_RECUPERACION.length())));
        }
        return codigo.toString();
    }

    /** Se guarda y se compara sin guion, mayúsculas ni espacios, para perdonar cómo se teclee. */
    private String normalizar(String codigo) {
        return codigo.replace("-", "").replace(" ", "").toLowerCase();
    }

    private User cargar(UUID usuarioId) {
        return userRepository.findById(usuarioId)
                .orElseThrow(() -> NotFoundException.de("Usuario", usuarioId));
    }
}
