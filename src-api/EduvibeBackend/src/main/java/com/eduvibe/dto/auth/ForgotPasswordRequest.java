package com.eduvibe.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Email de la cuenta cuya contraseña se quiere recuperar.
 */
public record ForgotPasswordRequest(

        @NotBlank(message = "El email es obligatorio")
        @Email(message = "El formato del email no es válido")
        String email) {
}
