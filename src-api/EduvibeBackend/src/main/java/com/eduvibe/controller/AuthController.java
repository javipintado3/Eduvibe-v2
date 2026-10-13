package com.eduvibe.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.eduvibe.dto.auth.AcceptInvitationRequest;
import com.eduvibe.dto.auth.AuthResponse;
import com.eduvibe.dto.auth.ChangePasswordRequest;
import com.eduvibe.dto.auth.ForgotPasswordRequest;
import com.eduvibe.dto.auth.InvitationInfoResponse;
import com.eduvibe.dto.auth.LoginRequest;
import com.eduvibe.dto.auth.RegisterRequest;
import com.eduvibe.dto.auth.TwoFactorCodeRequest;
import com.eduvibe.dto.auth.TwoFactorDisableRequest;
import com.eduvibe.dto.auth.TwoFactorEnabledResponse;
import com.eduvibe.dto.auth.TwoFactorSetupResponse;
import com.eduvibe.dto.auth.ResetPasswordRequest;
import com.eduvibe.dto.user.UserResponse;
import com.eduvibe.config.AppProperties;
import com.eduvibe.util.ClientIp;
import com.eduvibe.service.AuthService;
import com.eduvibe.service.InvitationService;
import com.eduvibe.service.PasswordResetService;
import com.eduvibe.service.RegistrationService;
import com.eduvibe.service.SessionService;
import com.eduvibe.service.TwoFactorService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Acceso a la plataforma.
 *
 * Las cuentas las crea un administrador ({@link UserController}) y se activan
 * aceptando una invitación. Quien no tiene invitación puede pedir una cuenta
 * ({@code /register}), pero eso solo crea una solicitud: no hay cuenta hasta
 * que un administrador la aprueba ({@link RegistrationRequestController}).
 *
 * El controlador se limita a recibir, delegar y devolver. Toda la lógica está
 * en los servicios, y los errores los traduce el manejador global, así que aquí
 * no hay un solo try/catch.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final InvitationService invitationService;
    private final PasswordResetService passwordResetService;
    private final TwoFactorService twoFactorService;
    private final SessionService sessionService;
    private final RegistrationService registrationService;
    private final AppProperties properties;

    /** Inicio de sesión con email y contraseña. */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request,
                                              HttpServletRequest http) {
        return ResponseEntity.ok(authService.login(request, http.getRemoteAddr()));
    }

    /** Datos de la sesión en curso. */
    @GetMapping("/me")
    public ResponseEntity<UserResponse> currentUser() {
        return ResponseEntity.ok(authService.currentUser());
    }

    /**
     * Comprueba un enlace de invitación y devuelve a quién corresponde, para
     * poder mostrarlo antes de pedir la contraseña.
     */
    @GetMapping("/invitations/{token}")
    public ResponseEntity<InvitationInfoResponse> lookupInvitation(@PathVariable String token) {
        return ResponseEntity.ok(invitationService.lookup(token));
    }

    /**
     * Establece la contraseña, activa la cuenta y devuelve ya la sesión
     * iniciada, para no pedir dos veces lo mismo.
     */
    @PostMapping("/invitations/{token}/accept")
    public ResponseEntity<AuthResponse> acceptInvitation(
            @PathVariable String token,
            @Valid @RequestBody AcceptInvitationRequest request) {

        return ResponseEntity.ok(authService.acceptInvitation(token, request));
    }

    /**
     * Pide una cuenta. Responde siempre igual, exista o no ya una cuenta con ese
     * email o corresponda su dominio a algún centro: distinguirlo permitiría
     * averiguar quién está dado de alta. La solicitud no llega a la
     * administración hasta que la persona confirma su correo.
     */
    @PostMapping("/register")
    public ResponseEntity<Void> requestRegistration(@Valid @RequestBody RegisterRequest request,
                                                  HttpServletRequest http) {
        registrationService.request(request,
                ClientIp.of(http, properties.registration().trustProxy()));
        return ResponseEntity.accepted().build();
    }

    /**
     * Confirma el correo de una solicitud con el enlace recibido. Es un POST y
     * no un GET para que un antivirus o la vista previa del cliente de correo,
     * que abren los enlaces por su cuenta, no puedan consumirlo.
     */
    @PostMapping("/register/verify/{token}")
    public ResponseEntity<Void> verifyRegistration(@PathVariable String token) {
        registrationService.verify(token);
        return ResponseEntity.noContent().build();
    }

    /**
     * Pide un enlace para elegir otra contraseña. Responde siempre igual, exista
     * o no el email: distinguirlo permitiría averiguar qué direcciones están
     * dadas de alta.
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotMyPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordResetService.request(request.email());
        return ResponseEntity.accepted().build();
    }

    /** Establece la contraseña nueva con el enlace recibido. */
    @PostMapping("/reset-password/{token}")
    public ResponseEntity<Void> resetPassword(
            @PathVariable String token,
            @Valid @RequestBody ResetPasswordRequest request) {

        passwordResetService.reset(token, request.password());
        return ResponseEntity.noContent().build();
    }

    /**
     * Cambia la contraseña de la sesión en curso, pidiendo la actual. Cierra las
     * demás sesiones y devuelve una nueva para que esta siga abierta.
     */
    @PutMapping("/password")
    public ResponseEntity<AuthResponse> changePassword(@Valid @RequestBody ChangePasswordRequest request,
                                                          HttpServletRequest http) {
        return ResponseEntity.ok(authService.changePassword(request, http.getRemoteAddr()));
    }

    /** Cierra la sesión: el token con el que se llama deja de valer desde este momento. */
    @PostMapping("/logout")
    public ResponseEntity<Void> closeSession() {
        sessionService.close(authService.currentIdentity());
        return ResponseEntity.noContent().build();
    }

    /** Primer paso del 2FA: genera el secreto y devuelve lo necesario para el QR. */
    @PostMapping("/2fa/setup")
    public ResponseEntity<TwoFactorSetupResponse> startTwoFactor() {
        return ResponseEntity.ok(twoFactorService.startConfiguration(authService.currentIdentity().id()));
    }

    /** Segundo paso: confirma con un código, activa y devuelve los códigos de recuperación. */
    @PostMapping("/2fa/enable")
    public ResponseEntity<TwoFactorEnabledResponse> enableTwoFactor(
            @Valid @RequestBody TwoFactorCodeRequest request) {

        return ResponseEntity.ok(new TwoFactorEnabledResponse(
                twoFactorService.enable(authService.currentIdentity().id(), request.code())));
    }

    /** Desactiva el 2FA, pidiendo la contraseña y un código. */
    @PostMapping("/2fa/disable")
    public ResponseEntity<Void> disableTwoFactor(@Valid @RequestBody TwoFactorDisableRequest request,
                                                   HttpServletRequest http) {

        twoFactorService.disable(authService.currentIdentity().id(),
                request.password(), request.code(), http.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }
}
