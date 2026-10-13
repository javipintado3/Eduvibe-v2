package com.eduvibe.dto.assignment;

import java.time.Instant;
import java.util.UUID;

import com.eduvibe.model.Assignment;

/**
 * Tarea tal y como aparece en la lista de trabajo de clase.
 *
 * La misma tarea se ve distinta según quién mire, y por eso hay dos campos que
 * se excluyen entre sí:
 *
 *  - a un alumno le importa en qué estado tiene su entrega (miEstado)
 *  - a un profesor, cuántas ha recibido (entregasRecibidas)
 *
 * El que no aplica viaja a null.
 */
public record AssignmentResponse(
        UUID id,
        UUID classId,
        UUID topicId,
        String title,
        Instant dueDate,
        int points,
        boolean haVencido,
        String miEstado,
        Boolean miEntregaTarde,
        Long entregasRecibidas,
        Instant createdAt) {

    /** Vista de alumno: su propio estado en la tarea. */
    public static AssignmentResponse forStudent(Assignment assignment, String miEstado, Boolean miEntregaTarde) {
        return new AssignmentResponse(
                assignment.getId(),
                assignment.getSchoolClass().getId(),
                assignment.getTopic() == null ? null : assignment.getTopic().getId(),
                assignment.getTitle(),
                assignment.getDueDate(),
                assignment.getPoints(),
                assignment.haVencido(),
                miEstado,
                miEntregaTarde,
                null,
                assignment.getCreatedAt());
    }

    /** Vista de profesor: cuántas entregas lleva recibidas. */
    public static AssignmentResponse forTeacher(Assignment assignment, long entregasRecibidas) {
        return new AssignmentResponse(
                assignment.getId(),
                assignment.getSchoolClass().getId(),
                assignment.getTopic() == null ? null : assignment.getTopic().getId(),
                assignment.getTitle(),
                assignment.getDueDate(),
                assignment.getPoints(),
                assignment.haVencido(),
                null,
                null,
                entregasRecibidas,
                assignment.getCreatedAt());
    }
}
