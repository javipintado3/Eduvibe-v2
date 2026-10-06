package com.eduvibe.dto.perfil;

import jakarta.validation.constraints.NotBlank;

/**
 * Confirmación para borrar la cuenta: la contraseña y, si la cuenta tiene la
 * verificación en dos pasos, un código (de la app o de recuperación).
 */
public record EraseAccountRequest(

        @NotBlank(message = "La contraseña es obligatoria")
        String password,

        String code) {
}
