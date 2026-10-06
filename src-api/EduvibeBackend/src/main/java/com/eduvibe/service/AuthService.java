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
import com.eduvibe.exception.BadRequestException;
import com.eduvibe.exception.NotFoundException;
import com.eduvibe.exception.TooManyRequestsException;
import com.eduvibe.exception.TwoFactorRequiredException;
import com.eduvibe.model.User;
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
     */
    private static final String CREDENCIALES_INVALIDAS = "Usuario y/o contraseña incorrectos";

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
    private static final String HASH_FICTICIO =
            new BCryptPasswordEncoder().encode(UUID.randomUUID().toString());

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final InvitationService invitationService;
    private final LoginAttemptService loginAttemptService;
    private final TwoFactorService twoFactorService;

    @Transactional
    public AuthResponse login(LoginRequest peticion, String ip) {
        String email = User.normalizarEmail(peticion.email());

        // Se comprueba antes de mirar la contraseña, y se aplica igual a emails
        // que existen y que no: así el bloqueo no delata qué direcciones hay
        if (loginAttemptService.estaBloqueado(email)) {
            throw new TooManyRequestsException("Demasiados intentos fallidos. Vuelve a intentarlo en "
                    + loginAttemptService.minutosDeBloqueo() + " minutos");
        }

        Optional<User> usuario = userRepository.findByEmail(email);

        String hash = usuario.map(User::getPasswordHash).orElse(HASH_FICTICIO);
        boolean contrasenaCorrecta = passwordEncoder.matches(peticion.password(), hash);

        // Email inexistente, cuenta pendiente o desactivada, y contraseña
        // incorrecta se tratan igual a propósito: el mismo mensaje de error y,
        // gracias a la comprobación de arriba, el mismo tiempo de respuesta.
        boolean credencialesValidas = usuario.isPresent()
                && usuario.get().puedeIniciarSesion()
                && contrasenaCorrecta;

        if (!credencialesValidas) {
            loginAttemptService.registrarFallo(email, ip);
            throw new BadCredentialsException(CREDENCIALES_INVALIDAS);
        }

        // Contraseña correcta: si la cuenta tiene el 2FA activado, falta el código.
        // Pedirlo no cuenta como fallo; equivocarse al darlo, sí.
        if (usuario.get().isTotpEnabled()) {
            if (peticion.totpCode() == null || peticion.totpCode().isBlank()) {
                throw new TwoFactorRequiredException();
            }
            if (!twoFactorService.codigoValido(usuario.get(), peticion.totpCode())) {
                loginAttemptService.registrarFallo(email, ip);
                throw new BadCredentialsException("Código de verificación incorrecto");
            }
        }

        loginAttemptService.registrarExito(email, ip);
        return construirRespuesta(usuario.get());
    }

    /**
     * Activa la cuenta con la contraseña elegida y deja a la persona dentro, sin
     * obligarla a volver a escribir lo que acaba de teclear.
     */
    @Transactional
    public AuthResponse aceptarInvitacion(String token, AcceptInvitationRequest peticion) {
        User usuario = invitationService.aceptar(token, peticion.password());
        return construirRespuesta(usuario);
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
    public void cambiarPassword(ChangePasswordRequest peticion, String ip) {
        AuthenticatedUser autenticado = identidadActual();
        User usuario = userRepository.findById(autenticado.id())
                .orElseThrow(() -> NotFoundException.de("Usuario", autenticado.id()));

        if (loginAttemptService.estaBloqueado(usuario.getEmail())) {
            throw new TooManyRequestsException("Demasiados intentos fallidos. Vuelve a intentarlo en "
                    + loginAttemptService.minutosDeBloqueo() + " minutos");
        }

        if (!passwordEncoder.matches(peticion.currentPassword(), usuario.getPasswordHash())) {
            loginAttemptService.registrarFallo(usuario.getEmail(), ip);
            throw new BadRequestException("La contraseña actual no es correcta");
        }

        if (passwordEncoder.matches(peticion.newPassword(), usuario.getPasswordHash())) {
            throw new BadRequestException("La contraseña nueva debe ser distinta de la actual");
        }

        usuario.cambiarPassword(passwordEncoder.encode(peticion.newPassword()));
        userRepository.save(usuario);
    }

    /** Usuario de la petición en curso, releído de base de datos. */
    @Transactional(readOnly = true)
    public UserResponse usuarioActual() {
        AuthenticatedUser autenticado = identidadActual();

        return userRepository.findById(autenticado.id())
                .map(UserResponse::de)
                .orElseThrow(() -> NotFoundException.de("Usuario", autenticado.id()));
    }

    /**
     * Identidad que el filtro ha dejado en el contexto de seguridad.
     *
     * @throws BadCredentialsException si no hay ninguna, lo que solo puede
     *         ocurrir si se llama desde una ruta pública por error.
     */
    public AuthenticatedUser identidadActual() {
        var autenticacion = SecurityContextHolder.getContext().getAuthentication();

        if (autenticacion == null || !(autenticacion.getPrincipal() instanceof AuthenticatedUser usuario)) {
            throw new BadCredentialsException("No hay ninguna sesión activa");
        }
        return usuario;
    }

    private AuthResponse construirRespuesta(User usuario) {
        return new AuthResponse(
                jwtService.emitirPara(usuario),
                jwtService.caducidadDeUnTokenNuevo(),
                UserResponse.de(usuario));
    }
}
