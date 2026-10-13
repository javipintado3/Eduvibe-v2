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

    public static NotificationResponse of(Notification announcement) {
        return new NotificationResponse(
                announcement.getId(),
                announcement.getType(),
                write(announcement),
                announcement.getPayload(),
                announcement.isRead(),
                announcement.getCreatedAt());
    }

    private static String write(Notification announcement) {
        Map<String, Object> data = announcement.getPayload() == null ? Map.of() : announcement.getPayload();
        String schoolClass = String.valueOf(data.getOrDefault("className", "tu clase"));
        String title = String.valueOf(data.getOrDefault("title", ""));

        return switch (announcement.getType()) {
            case Notification.NEW_ASSIGNMENT -> "Nueva tarea en " + schoolClass + ": " + title;
            case Notification.PUBLISHED_GRADE -> "Ya tienes nota en " + title + " (" + schoolClass + ")";
            case Notification.NEW_ANNOUNCEMENT -> "Nuevo aviso en " + schoolClass;
            case Notification.NEW_EXAM -> "Nuevo examen en " + schoolClass + ": " + title;
            case Notification.REGISTRATION_REQUESTED -> "Nueva solicitud de registro: " + title;
            default -> "Tienes una novedad";
        };
    }
}
