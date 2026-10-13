package com.eduvibe.exception;

import org.springframework.http.HttpStatus;

/**
 * La operación choca con el estado actual: un email ya dado de alta, una
 * invitación ya consumida...
 */
public class ConflictException extends ApiException {

    public ConflictException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
