package com.eduvibe.service;

import java.util.Optional;
import java.util.UUID;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.eduvibe.dto.auth.AcceptInvitationRequest;
import com.eduvibe.dto.auth.AuthResponse;
import com.eduvibe.dto.auth.ChangePasswordRequest;
import com.eduvibe.dto.auth.LoginRequest;
import com.eduvibe.dto.user.UserResponse;
import com.eduvibe.exception.AccountDisabledException;
import com.eduvibe.exception.BadRequestException;
import com.eduvibe.exception.NotFoundException;
import com.eduvibe.exception.TooManyRequestsException;
import com.eduvibe.exception.TwoFactorRequiredException;
import com.eduvibe.model.User;
import com.eduvibe.model.enums.UserStatus;
import com.eduvibe.repository.UserRepository;
import com.eduvibe.security.AuthenticatedUser;
import com.eduvibe.security.JwtService;

import lombok.RequiredArgsConstructor;

/**
 * Inicio de sesión y consulta de la sesión actual.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    /**
     * Mismo mensaje para email inexistente, contraseña incorrecta y cuenta no
     * activa. Diferenciarlos permitiría averiguar qué direcciones están dadas
     * de alta en la plataforma probando una a una.
     *
     * La pista sobre la invitación va en el mensaje de todos: una cuenta pendiente
     * no tiene contraseña con la que comprobar nada, así que avisarle solo a ella
     * delataría qué correos tienen una invitación sin aceptar.
     */
    private static final String INVALID_CREDENTIALS =
            "Usuario y/o contraseña incorrectos. Si aún no has activado tu cuenta, abre el enlace de invitación que recibiste por correo.";

    /**
     * Hash bcrypt válido de una contraseña que no existe.
     *
     * Se usa como sustituto cuando el email no está dado de alta, para que
     * {@code passwordEncoder.matches(...)} se ejecute igual en ese caso que
     * cuando sí hay usuario: es la operación lenta a propósito, y saltársela
     * dejaría una petición con email inexistente respondiendo más rápido que
     * una con email real y contraseña incorrecta. Esa diferencia de tiempo
     * delataría qué direcciones están registradas, aunque el mensaje de error
     * sea idéntico en los dos casos.
     */
    private static final String HASH_FAKE =
            new BCryptPasswordEncoder().encode(UUID.randomUUID().toString());

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ProtectedDemoAccounts protectedDemoAccounts;
    private final JwtService jwtService;
    private final InvitationService invitationService;
    private final LoginAttemptService loginAttemptService;
    private final TwoFactorService twoFactorService;

    @Transactional
    public AuthResponse login(LoginRequest request, String ip) {
        String email = User.normalizeEmail(request.email());

        // Se comprueba antes de mirar la contraseña, y se aplica igual a emails
        // que existen y que no: así el bloqueo no delata qué direcciones hay
        if (loginAttemptService.isLocked(email)) {
            throw new TooManyRequestsException("Demasiados intentos fallidos. Vuelve a intentarlo en "
                    + loginAttemptService.minutesOfLockout() + " minutos");
        }

        Optional<User> user = userRepository.findByEmail(email);

        String hash = user.map(User::getPasswordHash).orElse(HASH_FAKE);
        boolean correctPassword = passwordEncoder.matches(request.password(), hash);

        // Contraseña acertada en una cuenta desactivada: se le dice, porque quien
        // la sabe ya es dueño de la cuenta y "usuario o contraseña incorrectos"
        // solo lo confundiría. No cuenta como fallo (no es un intento de adivinar).
        // Una cuenta dada de baja por RGPD no llega aquí: pierde la contraseña.
        if (user.isPresent() && correctPassword && user.get().getStatus() == UserStatus.DISABLED) {
            throw new AccountDisabledException();
        }

        // Email inexistente, cuenta pendiente y contraseña incorrecta (también en
        // una cuenta desactivada) se tratan igual a propósito: el mismo mensaje y,
        // gracias a la comprobación de arriba, el mismo tiempo de respuesta.
        boolean validCredentials = user.isPresent()
                && user.get().canStartSession()
                && correctPassword;

        if (!validCredentials) {
            loginAttemptService.recordFailure(email, ip);
            throw new BadCredentialsException(INVALID_CREDENTIALS);
        }

        // Contraseña correcta: si la cuenta tiene el 2FA activado, falta el código.
        // Pedirlo no cuenta como fallo; equivocarse al darlo, sí.
        if (user.get().isTotpEnabled()) {
            if (request.totpCode() == null || request.totpCode().isBlank()) {
                throw new TwoFactorRequiredException();
            }
            if (!twoFactorService.validCode(user.get(), request.totpCode())) {
                loginAttemptService.recordFailure(email, ip);
                throw new BadCredentialsException("Código de verificación incorrecto");
            }
        }

        loginAttemptService.recordSuccess(email, ip);
        return buildAnswer(user.get());
    }

    /**
     * Activa la cuenta con la contraseña elegida y deja a la persona dentro, sin
     * obligarla a volver a escribir lo que acaba de teclear.
     */
    @Transactional
    public AuthResponse acceptInvitation(String token, AcceptInvitationRequest request) {
        User user = invitationService.accept(token, request.password());
        return buildAnswer(user);
    }

    /**
     * Cambia la contraseña de quien tiene la sesión abierta.
     *
     * Exige la actual. Un fallo cuenta como un intento de login fallido: si no,
     * quien robara un token podría probar contraseñas aquí sin ningún límite.
     * Se responde 400 y no 401 a propósito: el 401 hace que el frontend cierre
     * la sesión, y equivocarse al teclear la contraseña actual no es eso.
     */
    @Transactional
    public AuthResponse changePassword(ChangePasswordRequest request, String ip) {
        AuthenticatedUser autenticado = currentIdentity();
        User user = userRepository.findById(autenticado.id())
                .orElseThrow(() -> NotFoundException.of("Usuario", autenticado.id()));
        protectedDemoAccounts.requireNotProtected(user);

        if (loginAttemptService.isLocked(user.getEmail())) {
            throw new TooManyRequestsException("Demasiados intentos fallidos. Vuelve a intentarlo en "
                    + loginAttemptService.minutesOfLockout() + " minutos");
        }

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            loginAttemptService.recordFailure(user.getEmail(), ip);
            throw new BadRequestException("La contraseña actual no es correcta");
        }

        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new BadRequestException("La contraseña nueva debe ser distinta de la actual");
        }

        user.changePassword(passwordEncoder.encode(request.newPassword()));
        // Cierra el resto de sesiones: si alguien más tenía la cuenta abierta, deja de tenerla
        user.invalidateSessions();
        userRepository.save(user);

        // La sesión actual también se habría invalidado, así que se devuelve una nueva
        return buildAnswer(user);
    }

    /** Usuario de la petición en curso, releído de base de datos. */
    @Transactional(readOnly = true)
    public UserResponse currentUser() {
        AuthenticatedUser autenticado = currentIdentity();

        return userRepository.findById(autenticado.id())
                .map(UserResponse::of)
                .orElseThrow(() -> NotFoundException.of("Usuario", autenticado.id()));
    }

    /**
     * Identidad que el filtro ha dejado en el contexto de seguridad.
     *
     * @throws BadCredentialsException si no hay ninguna, lo que solo puede
     *         ocurrir si se llama desde una ruta pública por error.
     */
    public AuthenticatedUser currentIdentity() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            throw new BadCredentialsException("No hay ninguna sesión activa");
        }
        return user;
    }

    private AuthResponse buildAnswer(User user) {
        return new AuthResponse(
                jwtService.issueFor(user),
                jwtService.expiryOfTokenNew(),
                UserResponse.of(user));
    }
}
