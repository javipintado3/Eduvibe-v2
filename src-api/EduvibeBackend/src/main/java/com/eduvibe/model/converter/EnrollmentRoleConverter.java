package com.eduvibe.model.converter;

import com.eduvibe.model.enums.EnrollmentRole;

import jakarta.persistence.Converter;

/**
 * Conversión del papel dentro de una clase. La lógica está en {@link EnumWithValueConverter};
 * aquí solo se fija el tipo concreto, que es lo que JPA necesita para poder
 * aplicarlo automáticamente.
 */
@Converter(autoApply = true)
public class EnrollmentRoleConverter extends EnumWithValueConverter<EnrollmentRole> {

    public EnrollmentRoleConverter() {
        super(EnrollmentRole.class);
    }
}
