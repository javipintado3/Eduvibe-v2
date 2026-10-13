package com.eduvibe.model.enums;

/**
 * Situación de una entrega.
 *
 * No existe un estado "entregada tarde": llegar tarde no es un estado, es la
 * comparación entre submitted_at y la fecha límite de la tarea. Si fuese un
 * valor más de esta lista, una entrega calificada perdería la información de
 * que llegó fuera de plazo, porque no podría estar en los dos a la vez.
 */
public enum SubmissionStatus implements EnumWithValue {

    /** Empezada por el alumno pero todavía no enviada. */
    DRAFT("draft"),

    /** Enviada, pendiente de corrección. */
    SUBMITTED("submitted"),

    /** Ya corregida, con su calificación. */
    GRADED("graded");

    private final String value;

    SubmissionStatus(String value) {
        this.value = value;
    }

    @Override
    public String getValue() {
        return value;
    }

    public static SubmissionStatus fromValue(String value) {
        return EnumWithValue.from(SubmissionStatus.class, value);
    }
}
