package com.eduvibe.model.enums;

/**
 * Situación de una solicitud de registro.
 *
 * Una solicitud nace UNVERIFIED (el correo aún no está confirmado), pasa a
 * PENDING cuando la persona confirma que el correo es suyo, y termina
 * APPROVED o REJECTED por decisión de un administrador.
 */
public enum RegistrationStatus implements EnumWithValue {

    UNVERIFIED("unverified"),
    PENDING("pending"),
    APPROVED("approved"),
    REJECTED("rejected");

    private final String value;

    RegistrationStatus(String value) {
        this.value = value;
    }

    @Override
    public String getValue() {
        return value;
    }

    public static RegistrationStatus fromValue(String value) {
        return EnumWithValue.from(RegistrationStatus.class, value);
    }
}
