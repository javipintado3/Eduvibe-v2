package com.eduvibe.model.enums;

/**
 * Situación de una cuenta.
 *
 * El ciclo de vida es: el admin da de alta al usuario y la cuenta nace en
 * PENDING sin contraseña; cuando la persona acepta la invitación y establece su
 * contraseña pasa a ACTIVE; si causa baja se pasa a DISABLED. La fila nunca se borra
 * (ni siquiera al eliminar a alguien, que solo la anonimiza) para no perder el
 * histórico académico.
 *
 * Solo las cuentas ACTIVE pueden iniciar sesión.
 */
public enum UserStatus implements EnumConValor {

    PENDING("pending"),
    ACTIVE("active"),
    DISABLED("disabled");

    private final String valor;

    UserStatus(String valor) {
        this.valor = valor;
    }

    @Override
    public String getValor() {
        return valor;
    }

    public static UserStatus desdeValor(String valor) {
        return EnumConValor.desde(UserStatus.class, valor);
    }
}
