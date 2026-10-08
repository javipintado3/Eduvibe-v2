package com.eduvibe.dto.registration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Rol con el que se crea la cuenta al aprobar una solicitud. Lo elige quien
 * aprueba; quien solicita no puede pedirlo.
 */
public record ApproveRegistrationRequest(

        @NotBlank(message = "El rol es obligatorio")
        @Pattern(regexp = "admin|teacher|student|guardian",
                 message = "El rol debe ser admin, teacher, student o guardian")
        String role) {
}
