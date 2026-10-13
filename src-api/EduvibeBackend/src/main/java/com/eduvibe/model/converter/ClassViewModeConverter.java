package com.eduvibe.model.converter;

import com.eduvibe.model.enums.ClassViewMode;

import jakarta.persistence.Converter;

/**
 * Conversión del modo de vista de la clase. La lógica está en {@link EnumWithValueConverter}.
 */
@Converter(autoApply = true)
public class ClassViewModeConverter extends EnumWithValueConverter<ClassViewMode> {

    public ClassViewModeConverter() {
        super(ClassViewMode.class);
    }
}
