package com.eduvibe.model.converter;

import com.eduvibe.model.enums.ResourceType;

import jakarta.persistence.Converter;

/**
 * Conversión del tipo de material. La lógica está en {@link EnumWithValueConverter}.
 */
@Converter(autoApply = true)
public class ResourceTypeConverter extends EnumWithValueConverter<ResourceType> {

    public ResourceTypeConverter() {
        super(ResourceType.class);
    }
}
