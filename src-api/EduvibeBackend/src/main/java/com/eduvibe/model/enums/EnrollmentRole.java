package com.eduvibe.model.enums;

/**
 * Papel de una persona dentro de una clase concreta.
 *
 * Es independiente del rol en la organización: quien es TEACHER en el centro
 * solo imparte las clases en las que está matriculado como profesor. Una clase
 * admite varios profesores, y por eso esto vive en enrollments y no en una
 * columna de classes.
 */
public enum EnrollmentRole implements EnumWithValue {

    TEACHER("teacher"),
    STUDENT("student");

    private final String value;

    EnrollmentRole(String value) {
        this.value = value;
    }

    @Override
    public String getValue() {
        return value;
    }

    public static EnrollmentRole fromValue(String value) {
        return EnumWithValue.from(EnrollmentRole.class, value);
    }
}
