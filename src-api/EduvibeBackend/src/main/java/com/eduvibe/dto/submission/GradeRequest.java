package com.eduvibe.dto.submission;

import java.math.BigDecimal;
import java.util.List;

import com.eduvibe.dto.rubric.RubricScoreInput;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;

/**
 * Calificación que pone el profesorado.
 *
 * El máximo no se valida aquí sino en el servicio, porque depende de los puntos
 * que valga cada tarea y eso no se sabe hasta consultarla.
 *
 * @param score        la nota, cuando la tarea no tiene rúbrica. Con rúbrica
 *                     se ignora: la nota es la suma de {@code rubricScores},
 *                     que la calcula el servicio, no quien corrige a mano.
 * @param rubricScores puntuación de cada criterio, cuando la tarea tiene
 *                     rúbrica. Null o vacío si no la tiene.
 */
public record GradeRequest(

        @DecimalMin(value = "0.0", message = "La nota no puede ser negativa")
        @Digits(integer = 3, fraction = 2, message = "La nota admite como mucho dos decimales")
        BigDecimal score,

        @Size(max = 5000, message = "El comentario es demasiado largo")
        String feedback,

        @Valid
        List<RubricScoreInput> rubricScores) {

    public List<RubricScoreInput> rubricScoresOrNone() {
        return rubricScores == null ? List.of() : rubricScores;
    }
}
