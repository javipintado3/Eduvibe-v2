package com.eduvibe.dto.notification;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.eduvibe.model.Notification;

/**
 * Aviso dirigido a una persona.
 *
 * El texto no se guarda: se compone aquí a partir del tipo y del contenido
 * variable. Guardarlo ya redactado dejaría los avisos antiguos con la
 * redacción de la versión en la que se crearon, y obligaría a migrar textos
 * para corregir una errata.
 */
public record NotificationResponse(
        UUID id,
        String type,
        String texto,
        Map<String, Object> payload,
        boolean leida,
        Instant createdAt) {

    public static NotificationResponse de(Notification aviso) {
        return new NotificationResponse(
                aviso.getId(),
                aviso.getType(),
                redactar(aviso),
                aviso.getPayload(),
                aviso.estaLeida(),
                aviso.getCreatedAt());
    }

    private static String redactar(Notification aviso) {
        Map<String, Object> datos = aviso.getPayload() == null ? Map.of() : aviso.getPayload();
        String clase = String.valueOf(datos.getOrDefault("className", "tu clase"));
        String titulo = String.valueOf(datos.getOrDefault("title", ""));

        return switch (aviso.getType()) {
            case Notification.TAREA_NUEVA -> "Nueva tarea en " + clase + ": " + titulo;
            case Notification.NOTA_PUBLICADA -> "Ya tienes nota en " + titulo + " (" + clase + ")";
            case Notification.AVISO_NUEVO -> "Nuevo aviso en " + clase;
            case Notification.EXAMEN_NUEVO -> "Nuevo examen en " + clase + ": " + titulo;
            case Notification.REGISTRO_SOLICITADO -> "Nueva solicitud de registro: " + titulo;
            default -> "Tienes una novedad";
        };
    }
}
