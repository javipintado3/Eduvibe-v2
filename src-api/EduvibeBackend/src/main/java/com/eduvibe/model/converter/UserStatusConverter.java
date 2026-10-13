package com.eduvibe.model.converter;

import com.eduvibe.model.enums.UserStatus;

import jakarta.persistence.Converter;

/**
 * Conversión del estado de cuenta. La lógica está en {@link EnumWithValueConverter};
 * aquí solo se fija el tipo concreto, que es lo que JPA necesita para poder
 * aplicarlo automáticamente.
 */
@Converter(autoApply = true)
public class UserStatusConverter extends EnumWithValueConverter<UserStatus> {

    public UserStatusConverter() {
        super(UserStatus.class);
    }
}
