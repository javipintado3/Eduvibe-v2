package com.eduvibe.exception;

import org.springframework.http.HttpStatus;

/**
 * Base de los errores que la API sabe traducir a una respuesta concreta.
 *
 * Cada subclase lleva su código HTTP, de modo que el manejador global no tiene
 * que ir adivinando qué significa cada excepción. En la versión anterior había
 * una única excepción anotada con @ResponseStatus(NOT_FOUND) y por eso un
 * intento de inicio de sesión fallido contestaba 404.
 */
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;

    protected ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
