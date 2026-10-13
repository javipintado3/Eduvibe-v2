package com.eduvibe.model.enums;

/**
 * Enumerado que se guarda en base de datos con un valor propio, distinto del
 * nombre de la constante.
 *
 * Todos los enumerados del modelo lo son: en Java se escriben en mayúsculas,
 * como manda la convención, y en la base de datos en minúsculas, que es lo que
 * esperan las restricciones CHECK de las columnas. Esta interfaz permite que
 * una sola conversión sirva para todos.
 */
public interface EnumWithValue {

    /** Valor tal y como se almacena y viaja por la API. */
    String getValue();

    /**
     * Busca la constante que corresponde a un valor almacenado.
     *
     * @throws IllegalArgumentException si no corresponde a ninguna, con la
     *         lista de las admitidas para que el mensaje sea útil.
     */
    static <E extends Enum<E> & EnumWithValue> E from(Class<E> tipo, String value) {
        for (E constant : tipo.getEnumConstants()) {
            if (constant.getValue().equalsIgnoreCase(value)) {
                return constant;
            }
        }
        StringBuilder accepted = new StringBuilder();
        for (E constant : tipo.getEnumConstants()) {
            if (accepted.length() > 0) {
                accepted.append(", ");
            }
            accepted.append(constant.getValue());
        }
        throw new IllegalArgumentException(
                "Valor no reconocido: '" + value + "'. Los admitidos son: " + accepted);
    }
}
