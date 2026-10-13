package com.eduvibe.model.converter;

import com.eduvibe.model.enums.EnumWithValue;

import jakarta.persistence.AttributeConverter;

/**
 * Conversión común para todos los enumerados del modelo.
 *
 * Cada enumerado solo necesita una subclase de tres líneas anotada con
 * {@code @Converter(autoApply = true)}, en lugar de repetir la misma pareja de
 * métodos una y otra vez. JPA exige que el convertidor declare tipos concretos,
 * y de ahí que las subclases existan; pero la lógica vive aquí.
 */
public abstract class EnumWithValueConverter<E extends Enum<E> & EnumWithValue>
        implements AttributeConverter<E, String> {

    private final Class<E> tipo;

    protected EnumWithValueConverter(Class<E> tipo) {
        this.tipo = tipo;
    }

    @Override
    public String convertToDatabaseColumn(E constant) {
        return constant == null ? null : constant.getValue();
    }

    @Override
    public E convertToEntityAttribute(String value) {
        return value == null ? null : EnumWithValue.from(tipo, value);
    }
}
