package com.eduvibe.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Solicitud de registro hecha desde el formulario público.
 *
 * No lleva contraseña (la elige la persona al aceptar su invitación) ni rol (lo
 * fija quien aprueba): lo que no se acepta aquí no se puede falsear.
 *
 * {@code message} es un texto libre opcional para la administración (quién es,
 * en qué curso está...): solo se muestra, nunca se interpreta.
 *
 * {@code website} es un cebo: el formulario lo oculta, así que una persona
 * nunca lo rellena, pero un bot que rellena todos los campos sí. Si llega con
 * contenido, la solicitud se descarta sin avisar.
 */
public record RegisterRequest(

        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 120, message = "El nombre no puede superar los 120 caracteres")
        String name,

        @NotBlank(message = "El email es obligatorio")
        @Email(message = "El formato del email no es válido")
        @Size(max = 255, message = "El email no puede superar los 255 caracteres")
        String email,

        @Size(max = 500, message = "El mensaje no puede superar los 500 caracteres")
        String message,

        @Size(max = 200)
        String website) {

    public boolean pareceUnBot() {
        return website != null && !website.isBlank();
    }
}
