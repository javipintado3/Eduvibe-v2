package com.eduvibe.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * @param totpCode código de verificación en dos pasos; solo hace falta si la
 *                 cuenta lo tiene activado, y puede ser el de la app o uno de recuperación
 */
public record LoginRequest(

        @NotBlank(message = "El email es obligatorio")
        @Email(message = "El formato del email no es válido")
        String email,

        @NotBlank(message = "La contraseña es obligatoria")
        String password,

        String totpCode) {

    /** Inicio de sesión sin código, el caso de las cuentas sin 2FA. */
    public LoginRequest(String email, String password) {
        this(email, password, null);
    }
}
