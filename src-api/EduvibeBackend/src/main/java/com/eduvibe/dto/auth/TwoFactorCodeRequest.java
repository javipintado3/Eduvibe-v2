package com.eduvibe.dto.auth;

import jakarta.validation.constraints.NotBlank;

/**
 * Código de 6 dígitos de la app de autenticación.
 */
public record TwoFactorCodeRequest(

        @NotBlank(message = "El código es obligatorio")
        String code) {
}
