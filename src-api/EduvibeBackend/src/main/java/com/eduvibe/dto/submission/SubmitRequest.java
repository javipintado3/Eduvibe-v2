package com.eduvibe.dto.submission;

import jakarta.validation.constraints.Size;

/**
 * Lo que manda un alumno al trabajar en una tarea.
 *
 * @param content  respuesta escrita
 * @param fileUrl  enlace al archivo entregado
 * @param enviar   true envía la entrega; false o ausente la deja como borrador,
 *                 de modo que el alumno pueda guardar y seguir más tarde
 */
public record SubmitRequest(

        @Size(max = 20000, message = "La respuesta es demasiado larga")
        String content,

        @Size(max = 2000, message = "El enlace es demasiado largo")
        String fileUrl,

        Boolean enviar) {

    /** Sin indicar nada, se entiende que es un borrador. */
    public boolean wantsToSend() {
        return Boolean.TRUE.equals(enviar);
    }
}
