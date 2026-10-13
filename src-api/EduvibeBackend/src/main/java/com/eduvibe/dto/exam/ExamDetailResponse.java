package com.eduvibe.dto.exam;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.eduvibe.model.Exam;

/**
 * Pantalla de aterrizaje de un examen: metadatos y, si es alumnado, el
 * resumen de su propio intento.
 *
 * Deliberadamente sin preguntas: para el alumnado, verlas solo tiene sentido
 * al empezar el intento (así cuenta el tiempo desde ese momento, no desde
 * que se entra a mirar); para el profesorado, {@code GET /api/exams/{id}/questions}
 * es la vista de repaso con las respuestas correctas.
 */
public record ExamDetailResponse(
        UUID id,
        UUID classId,
        String className,
        String classSubject,
        UUID topicId,
        String title,
        String description,
        int durationMinutes,
        Instant dueDate,
        boolean haVencido,
        boolean puedoEditar,
        int numeroPreguntas,
        int totalPuntos,
        String miEstado,
        BigDecimal miNota,
        Instant createdAt) {

    public static ExamDetailResponse of(Exam exam, boolean puedoEditar, int numeroPreguntas, int totalPuntos,
                                        String miEstado, BigDecimal miNota) {
        return new ExamDetailResponse(
                exam.getId(),
                exam.getSchoolClass().getId(),
                exam.getSchoolClass().getName(),
                exam.getSchoolClass().getSubject(),
                exam.getTopic() == null ? null : exam.getTopic().getId(),
                exam.getTitle(),
                exam.getDescription(),
                exam.getDurationMinutes(),
                exam.getDueDate(),
                exam.haVencido(),
                puedoEditar,
                numeroPreguntas,
                totalPuntos,
                miEstado,
                miNota,
                exam.getCreatedAt());
    }
}
