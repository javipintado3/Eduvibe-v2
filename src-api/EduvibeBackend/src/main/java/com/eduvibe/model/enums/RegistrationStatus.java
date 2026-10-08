package com.eduvibe.model.enums;

/**
 * Situación de una solicitud de registro.
 *
 * Una solicitud nace UNVERIFIED (el correo aún no está confirmado), pasa a
 * PENDING cuando la persona confirma que el correo es suyo, y termina
 * APPROVED o REJECTED por decisión de un administrador.
 */
public enum RegistrationStatus implements EnumConValor {

    UNVERIFIED("unverified"),
    PENDING("pending"),
    APPROVED("approved"),
    REJECTED("rejected");

    private final String valor;

    RegistrationStatus(String valor) {
        this.valor = valor;
    }

    @Override
    public String getValor() {
        return valor;
    }

    public static RegistrationStatus desdeValor(String valor) {
        return EnumConValor.desde(RegistrationStatus.class, valor);
    }
}
