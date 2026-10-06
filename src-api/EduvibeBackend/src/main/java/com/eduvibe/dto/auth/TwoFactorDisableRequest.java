package com.eduvibe.dto.auth;

import jakarta.validation.constraints.NotBlank;

/**
 * Desactivar el 2FA exige la contraseña y un código (de la app o de recuperación).
 */
public record TwoFactorDisableRequest(

        @NotBlank(message = "La contraseña es obligatoria")
        String password,

        @NotBlank(message = "El código es obligatorio")
        String code) {
}
