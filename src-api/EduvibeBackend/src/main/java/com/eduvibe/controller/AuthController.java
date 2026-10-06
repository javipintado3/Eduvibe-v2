package com.eduvibe.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.eduvibe.dto.auth.AcceptInvitationRequest;
import com.eduvibe.dto.auth.AuthResponse;
import com.eduvibe.dto.auth.ForgotPasswordRequest;
import com.eduvibe.dto.auth.InvitationInfoResponse;
import com.eduvibe.dto.auth.LoginRequest;
import com.eduvibe.dto.auth.ResetPasswordRequest;
import com.eduvibe.dto.user.UserResponse;
import com.eduvibe.service.AuthService;
import com.eduvibe.service.InvitationService;
import com.eduvibe.service.PasswordResetService;

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
}
