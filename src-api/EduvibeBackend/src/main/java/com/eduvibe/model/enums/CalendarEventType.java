package com.eduvibe.model.enums;

/**
 * Tipo de evento introducido a mano en el calendario.
 *
 * Las entregas no están aquí: se derivan de la fecha límite de las tareas, para
 * no tener el mismo dato en dos sitios y que puedan dejar de coincidir.
 */
public enum CalendarEventType implements EnumWithValue {

    EXAM("exam"),
    HOLIDAY("holiday"),
    OTHER("other");

    private final String value;

    CalendarEventType(String value) {
        this.value = value;
    }

    @Override
    public String getValue() {
        return value;
    }

    public static CalendarEventType fromValue(String value) {
        return EnumWithValue.from(CalendarEventType.class, value);
    }
}
