package com.eduvibe.model.converter;

import com.eduvibe.model.enums.RegistrationStatus;

import jakarta.persistence.Converter;

/**
 * Conversión del estado de una solicitud de registro. La lógica está en
 * {@link EnumConValorConverter}; aquí solo se fija el tipo concreto, que es lo
 * que JPA necesita para poder aplicarlo automáticamente.
 */
@Converter(autoApply = true)
public class RegistrationStatusConverter extends EnumConValorConverter<RegistrationStatus> {

    public RegistrationStatusConverter() {
        super(RegistrationStatus.class);
    }
}
