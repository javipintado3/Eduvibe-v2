package com.eduvibe.model.enums;

/**
 * Naturaleza de un material de clase.
 *
 * Sirve para elegir el icono con el que se muestra en la lista: reconocer de
 * un vistazo si algo es un PDF o un vídeo ahorra tener que abrirlo.
 */
public enum ResourceType implements EnumWithValue {

    PDF("pdf"),
    LINK("link"),
    VIDEO("video"),
    DOC("doc"),
    OTHER("other");

    private final String value;

    ResourceType(String value) {
        this.value = value;
    }

    @Override
    public String getValue() {
        return value;
    }

    public static ResourceType fromValue(String value) {
        return EnumWithValue.from(ResourceType.class, value);
    }
}
