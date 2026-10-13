package com.eduvibe.dto.schoolclass;

import java.time.Instant;
import java.util.UUID;

import com.eduvibe.model.SchoolClass;

/**
 * Tarjeta de clase del panel principal.
 *
 * Incluye la próxima entrega porque es justo lo que hace útil esa pantalla: ver
 * de un vistazo qué toca, sin entrar en cada clase.
 *
 * @param miRol          papel de quien consulta dentro de la clase
 * @param proximaEntrega fecha de la siguiente tarea con plazo, o null si no hay
 * @param profesores     nombres del profesorado, para la línea bajo el título
 * @param alumnado       número de alumnos matriculados, para el panel de gestión de administración
 */
public record ClassResponse(
        UUID id,
        String name,
        String subject,
        String color,
        String imageUrl,
        String miRol,
        Instant proximaEntrega,
        java.util.List<String> profesores,
        Instant createdAt,
        String viewMode,
        int alumnado) {

    public static ClassResponse of(SchoolClass schoolClass, String miRol,
                                   Instant proximaEntrega, java.util.List<String> profesores, int alumnado) {
        return new ClassResponse(
                schoolClass.getId(),
                schoolClass.getName(),
                schoolClass.getSubject(),
                schoolClass.getColor(),
                schoolClass.getImageUrl(),
                miRol,
                proximaEntrega,
                profesores,
                schoolClass.getCreatedAt(),
                schoolClass.getViewMode().getValue(),
                alumnado);
    }
}
