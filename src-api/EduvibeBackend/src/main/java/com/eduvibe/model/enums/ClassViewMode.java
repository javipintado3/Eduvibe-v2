package com.eduvibe.model.enums;

/**
 * Cómo se organiza el contenido de una clase.
 *
 * Decide qué pantalla ve quien entra en la clase: temas con actividades y
 * materiales separados (primaria/secundaria), o módulos con todo mezclado
 * (universidad). Se elige por clase, no globalmente para toda la plataforma.
 */
public enum ClassViewMode implements EnumWithValue {

    STRUCTURED("structured"),
    FLEXIBLE("flexible");

    private final String value;

    ClassViewMode(String value) {
        this.value = value;
    }

    @Override
    public String getValue() {
        return value;
    }

    public static ClassViewMode fromValue(String value) {
        return EnumWithValue.from(ClassViewMode.class, value);
    }
}
