package com.eduvibe.dto.resource;

import java.time.Instant;
import java.util.UUID;

import com.eduvibe.model.Resource;

/**
 * @param availableFrom desde cuándo lo ve el alumnado; null si no tiene restricción
 * @param bloqueado      si quien consulta todavía no puede verlo. Al profesorado
 *                       nunca se le bloquea, así que también sirve para saber a
 *                       quién mostrarle el aviso de "programado".
 */
public record ResourceResponse(
        UUID id,
        UUID topicId,
        String title,
        String fileUrl,
        String type,
        int sortOrder,
        Instant availableFrom,
        boolean bloqueado,
        Instant createdAt) {

    /**
     * @param isTeacher si quien consulta puede editar la clase: ve el material
     *                   siempre, aunque todavía esté programado para el futuro.
     */
    public static ResourceResponse of(Resource material, boolean isTeacher) {
        boolean bloqueado = !isTeacher && material.lockedForStudents();

        return new ResourceResponse(
                material.getId(),
                material.getTopic() == null ? null : material.getTopic().getId(),
                material.getTitle(),
                // Mientras esté bloqueado no se manda el archivo: ocultar el
                // enlace en el HTML no impediría descargarlo llamando a la API
                bloqueado ? null : material.getFileUrl(),
                material.getType() == null ? null : material.getType().getValue(),
                material.getSortOrder(),
                material.getAvailableFrom(),
                bloqueado,
                material.getCreatedAt());
    }
}
