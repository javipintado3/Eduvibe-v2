package com.eduvibe.dto.exam;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Un examen completo, con sus preguntas y opciones, tal y como se crea o se
 * edita de una sola vez: no tiene sentido dar de alta un examen sin
 * preguntas y añadírselas después una a una.
 *
 * Las preguntas pueden venir de dos sitios —nuevas ({@code questions}) o
 * reutilizadas del banco de la clase ({@code reuseQuestions})—, y hace falta
 * al menos una entre las dos; por eso ninguna de las dos lleva
 * {@code @NotEmpty} y la comprobación vive en {@link com.eduvibe.service.ExamService}.
 */
public record SaveExamRequest(

        @NotBlank(message = "El título es obligatorio")
        @Size(max = 200, message = "El título no puede superar los 200 caracteres")
        String title,

        @Size(max = 20000, message = "El enunciado es demasiado largo")
        String description,

        @NotNull(message = "La duración es obligatoria")
        @Positive(message = "La duración debe ser mayor que cero")
        @Max(value = 480, message = "La duración no puede pasar de 480 minutos")
        Integer durationMinutes,

        Instant dueDate,

        UUID topicId,

        @Valid
        List<QuestionInput> questions,

        @Valid
        List<ReuseQuestionInput> reuseQuestions) {

    /** Sin preguntas nuevas, cuando el examen se arma solo con el banco. */
    public List<QuestionInput> questionsOrNone() {
        return questions == null ? List.of() : questions;
    }

    /** Sin reutilizar nada, cuando todas las preguntas son nuevas. */
    public List<ReuseQuestionInput> reuseQuestionsOrNone() {
        return reuseQuestions == null ? List.of() : reuseQuestions;
    }

    public record QuestionInput(

            @NotBlank(message = "La pregunta no puede estar vacía")
            @Size(max = 2000, message = "La pregunta es demasiado larga")
            String text,

            @Positive(message = "Los puntos deben ser mayores que cero")
            Integer points,

            @NotEmpty(message = "Cada pregunta necesita al menos dos opciones")
            @Size(min = 2, message = "Cada pregunta necesita al menos dos opciones")
            @Valid
            List<OptionInput> options) {

        public int pointsOrDefault() {
            return points == null ? 1 : points;
        }
    }

    public record OptionInput(

            @NotBlank(message = "La opción no puede estar vacía")
            @Size(max = 500, message = "La opción es demasiado larga")
            String text,

            boolean correct) {
    }

    /** Una pregunta ya existente del banco de la clase, con sus propios puntos para este examen. */
    public record ReuseQuestionInput(

            @NotNull(message = "Falta el identificador de la pregunta")
            UUID questionId,

            @Positive(message = "Los puntos deben ser mayores que cero")
            Integer points) {

        public int pointsOrOriginal(int originalPoints) {
            return points == null ? originalPoints : points;
        }
    }
}
