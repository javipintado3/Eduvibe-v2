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
import com.eduvibe.dto.auth.TwoFactorCodeRequest;
import com.eduvibe.dto.auth.TwoFactorDisableRequest;
import com.eduvibe.dto.auth.TwoFactorEnabledResponse;
import com.eduvibe.dto.auth.TwoFactorSetupResponse;
import com.eduvibe.dto.auth.ResetPasswordRequest;
import com.eduvibe.dto.user.UserResponse;
import com.eduvibe.service.AuthService;
import com.eduvibe.service.InvitationService;
import com.eduvibe.service.PasswordResetService;
import com.eduvibe.service.SesionService;
import com.eduvibe.service.TwoFactorService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Acceso a la plataforma.
 *
 * No hay endpoint de registro: las cuentas las crea un administrador
 * ({@link UserController}) y se activan aceptando una invitación.
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
    private final SesionService sesionService;

    /** Inicio de sesión con email y contraseña. */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest peticion,
                                              HttpServletRequest http) {
        return ResponseEntity.ok(authService.login(peticion, http.getRemoteAddr()));
    }

    /** Datos de la sesión en curso. */
    @GetMapping("/me")
    public ResponseEntity<UserResponse> usuarioActual() {
        return ResponseEntity.ok(authService.usuarioActual());
    }

    /**
     * Comprueba un enlace de invitación y devuelve a quién corresponde, para
     * poder mostrarlo antes de pedir la contraseña.
     */
    @GetMapping("/invitations/{token}")
    public ResponseEntity<InvitationInfoResponse> consultarInvitacion(@PathVariable String token) {
        return ResponseEntity.ok(invitationService.consultar(token));
    }

    /**
     * Establece la contraseña, activa la cuenta y devuelve ya la sesión
     * iniciada, para no pedir dos veces lo mismo.
     */
    @PostMapping("/invitations/{token}/accept")
    public ResponseEntity<AuthResponse> aceptarInvitacion(
            @PathVariable String token,
            @Valid @RequestBody AcceptInvitationRequest peticion) {

        return ResponseEntity.ok(authService.aceptarInvitacion(token, peticion));
    }

    /**
     * Pide un enlace para elegir otra contraseña. Responde siempre igual, exista
     * o no el email: distinguirlo permitiría averiguar qué direcciones están
     * dadas de alta.
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<Void> olvideMiContrasena(@Valid @RequestBody ForgotPasswordRequest peticion) {
        passwordResetService.solicitar(peticion.email());
        return ResponseEntity.accepted().build();
    }

    /** Establece la contraseña nueva con el enlace recibido. */
    @PostMapping("/reset-password/{token}")
    public ResponseEntity<Void> restablecerContrasena(
            @PathVariable String token,
            @Valid @RequestBody ResetPasswordRequest peticion) {

        passwordResetService.restablecer(token, peticion.password());
        return ResponseEntity.noContent().build();
    }

    /**
     * Cambia la contraseña de la sesión en curso, pidiendo la actual. Cierra las
     * demás sesiones y devuelve una nueva para que esta siga abierta.
     */
    @PutMapping("/password")
    public ResponseEntity<AuthResponse> cambiarContrasena(@Valid @RequestBody ChangePasswordRequest peticion,
                                                          HttpServletRequest http) {
        return ResponseEntity.ok(authService.cambiarPassword(peticion, http.getRemoteAddr()));
    }

    /** Cierra la sesión: el token con el que se llama deja de valer desde este momento. */
    @PostMapping("/logout")
    public ResponseEntity<Void> cerrarSesion() {
        sesionService.cerrar(authService.identidadActual());
        return ResponseEntity.noContent().build();
    }

    /** Primer paso del 2FA: genera el secreto y devuelve lo necesario para el QR. */
    @PostMapping("/2fa/setup")
    public ResponseEntity<TwoFactorSetupResponse> iniciarDosPasos() {
        return ResponseEntity.ok(twoFactorService.iniciarConfiguracion(authService.identidadActual().id()));
    }

    /** Segundo paso: confirma con un código, activa y devuelve los códigos de recuperación. */
    @PostMapping("/2fa/enable")
    public ResponseEntity<TwoFactorEnabledResponse> activarDosPasos(
            @Valid @RequestBody TwoFactorCodeRequest peticion) {

        return ResponseEntity.ok(new TwoFactorEnabledResponse(
                twoFactorService.activar(authService.identidadActual().id(), peticion.code())));
    }

    /** Desactiva el 2FA, pidiendo la contraseña y un código. */
    @PostMapping("/2fa/disable")
    public ResponseEntity<Void> desactivarDosPasos(@Valid @RequestBody TwoFactorDisableRequest peticion,
                                                   HttpServletRequest http) {

        twoFactorService.desactivar(authService.identidadActual().id(),
                peticion.password(), peticion.code(), http.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }
}
