package com.eduvibe.dto.common;

import java.time.Instant;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Forma única de los errores de la API, para que el frontend no tenga que
 * adivinar dónde viene el mensaje según el tipo de fallo.
 *
 * @param status    código HTTP
 * @param error     nombre corto del código
 * @param message   mensaje destinado a mostrarse al usuario
 * @param path      ruta que ha fallado
 * @param campos    errores por campo, solo en fallos de validación
 * @param timestamp momento del fallo
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        int status,
        String error,
        String message,
        String path,
        Map<String, String> campos,
        Instant timestamp) {

    public static ErrorResponse of(int status, String error, String message, String path) {
        return new ErrorResponse(status, error, message, path, null, Instant.now());
    }

    public static ErrorResponse ofValidation(String path, Map<String, String> campos) {
        return new ErrorResponse(400, "Bad Request", "Hay campos con errores", path, campos, Instant.now());
    }
}
