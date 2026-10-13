package com.eduvibe.dto.assignment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Datos de una tarea al crearla o al editarla.
 *
 * Se usa el mismo DTO para las dos operaciones porque los campos y sus reglas
 * son exactamente los mismos; tener dos copias idénticas solo garantizaría que
 * algún día se cambie una y no la otra.
 *
 * @param dueDate fecha límite. Null significa que la tarea no tiene plazo, no
 *                que se deje como estaba.
 * @param topicId tema al que pertenece. Null la deja suelta.
 */
public record SaveAssignmentRequest(

        @NotBlank(message = "El título es obligatorio")
        @Size(max = 200, message = "El título no puede superar los 200 caracteres")
        String title,

        @Size(max = 20000, message = "El enunciado es demasiado largo")
        String description,

        Instant dueDate,

        @Positive(message = "La puntuación debe ser mayor que cero")
        @Max(value = 1000, message = "La puntuación no puede pasar de 1000")
        Integer points,

        @Min(value = 0, message = "La penalización no puede ser negativa")
        @Max(value = 100, message = "La penalización no puede pasar del 100%")
        Integer latePenaltyPercent,

        @DecimalMin(value = "0.01", message = "El peso debe ser mayor que cero")
        @DecimalMax(value = "99.99", message = "El peso es demasiado grande")
        BigDecimal weight,

        /** Si se entrega y se corrige una vez por subgrupo. Sin indicar, es individual. */
        Boolean groupAssignment,

        UUID topicId) {

    /** Si no se indica puntuación, se toma la de un examen al uso. */
    public int pointsOrDefault() {
        return points == null ? 100 : points;
    }

    /** Sin indicar, no hay penalización: es el comportamiento de siempre. */
    public int penaltyOrDefault() {
        return latePenaltyPercent == null ? 0 : latePenaltyPercent;
    }

    /** Sin indicar, peso normal: cuenta igual que cualquier otra tarea. */
    public BigDecimal weightOrDefault() {
        return weight == null ? BigDecimal.ONE : weight;
    }

    /** Sin indicar, individual: es el comportamiento de siempre. */
    public boolean isGroupOrDefault() {
        return groupAssignment != null && groupAssignment;
    }
}
