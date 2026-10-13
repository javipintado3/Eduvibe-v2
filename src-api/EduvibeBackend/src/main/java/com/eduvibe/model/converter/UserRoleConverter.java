package com.eduvibe.model.converter;

import com.eduvibe.model.enums.UserRole;

import jakarta.persistence.Converter;

/**
 * Conversión del rol de usuario. La lógica está en {@link EnumWithValueConverter};
 * aquí solo se fija el tipo concreto, que es lo que JPA necesita para poder
 * aplicarlo automáticamente.
 */
@Converter(autoApply = true)
public class UserRoleConverter extends EnumWithValueConverter<UserRole> {

    public UserRoleConverter() {
        super(UserRole.class);
    }
}
