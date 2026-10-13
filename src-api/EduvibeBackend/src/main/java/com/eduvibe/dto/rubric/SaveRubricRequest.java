package com.eduvibe.dto.rubric;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/**
 * Una rúbrica completa, con todos sus criterios de una vez: no tiene sentido
 * crearla sin ninguno y añadírselos después uno a uno.
 *
 * Reemplaza siempre a la rúbrica anterior si ya existía (ver
 * {@link com.eduvibe.service.RubricService#save}); no hay un PATCH
 * criterio a criterio.
 */
public record SaveRubricRequest(

        @NotEmpty(message = "La rúbrica necesita al menos un criterio")
        @Valid
        List<CriterionInput> criteria) {

    public record CriterionInput(

            @NotBlank(message = "La descripción del criterio es obligatoria")
            @Size(max = 500, message = "La descripción es demasiado larga")
            String description,

            @DecimalMin(value = "0.01", message = "La puntuación máxima debe ser mayor que cero")
            BigDecimal maxPoints) {
    }
}
