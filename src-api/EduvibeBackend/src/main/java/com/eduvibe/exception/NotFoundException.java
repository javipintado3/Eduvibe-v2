package com.eduvibe.exception;

import org.springframework.http.HttpStatus;

/** El recurso solicitado no existe. */
public class NotFoundException extends ApiException {

    public NotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, message);
    }

    public static NotFoundException of(String entity, Object id) {
        return new NotFoundException(entity + " no encontrado: " + id);
    }
}
