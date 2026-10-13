package com.eduvibe.dto.exam;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.eduvibe.model.Exam;

/**
 * Examen tal y como aparece en la lista de la clase.
 *
 * Igual que {@link com.eduvibe.dto.assignment.AssignmentResponse}: al
 * alumnado le importa el estado de su propio intento, al profesorado cuántos
 * ha recibido. El que no aplica viaja a null.
 */
public record ExamResponse(
        UUID id,
        UUID classId,
        UUID topicId,
        String title,
        int durationMinutes,
        Instant dueDate,
        boolean haVencido,
        int numeroPreguntas,
        String miEstado,
        BigDecimal miNota,
        Long intentosRecibidos,
        Instant createdAt) {

    /** Vista de alumno: el estado de su propio intento ("no_empezado", "en_curso", "entregado"). */
    public static ExamResponse forStudent(Exam exam, int numeroPreguntas, String miEstado, BigDecimal miNota) {
        return new ExamResponse(
                exam.getId(),
                exam.getSchoolClass().getId(),
                exam.getTopic() == null ? null : exam.getTopic().getId(),
                exam.getTitle(),
                exam.getDurationMinutes(),
                exam.getDueDate(),
                exam.haVencido(),
                numeroPreguntas,
                miEstado,
                miNota,
                null,
                exam.getCreatedAt());
    }

    /** Vista de profesor: cuántos intentos lleva recibidos. */
    public static ExamResponse forTeacher(Exam exam, int numeroPreguntas, long intentosRecibidos) {
        return new ExamResponse(
                exam.getId(),
                exam.getSchoolClass().getId(),
                exam.getTopic() == null ? null : exam.getTopic().getId(),
                exam.getTitle(),
                exam.getDurationMinutes(),
                exam.getDueDate(),
                exam.haVencido(),
                numeroPreguntas,
                null,
                null,
                intentosRecibidos,
                exam.getCreatedAt());
    }
}
