package com.eduvibe.model.converter;

import com.eduvibe.model.enums.SubmissionStatus;

import jakarta.persistence.Converter;

/**
 * Conversión de la situación de una entrega. La lógica está en {@link EnumWithValueConverter};
 * aquí solo se fija el tipo concreto, que es lo que JPA necesita para poder
 * aplicarlo automáticamente.
 */
@Converter(autoApply = true)
public class SubmissionStatusConverter extends EnumWithValueConverter<SubmissionStatus> {

    public SubmissionStatusConverter() {
        super(SubmissionStatus.class);
    }
}
