package com.eduvibe.exception;

import org.springframework.http.HttpStatus;

/** La petición es válida en forma pero no en contenido. */
public class BadRequestException extends ApiException {

    public BadRequestException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
