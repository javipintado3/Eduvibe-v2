package com.eduvibe.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cambio de contraseña desde el perfil: se pide también la actual, para que
 * quien encuentre una sesión abierta no pueda dejarse la cuenta.
 */
public record ChangePasswordRequest(

        @NotBlank(message = "La contraseña actual es obligatoria")
        String currentPassword,

        @NotBlank(message = "La contraseña nueva es obligatoria")
        @Size(min = 8, max = 100, message = "La contraseña nueva debe tener al menos 8 caracteres")
        String newPassword) {
}
