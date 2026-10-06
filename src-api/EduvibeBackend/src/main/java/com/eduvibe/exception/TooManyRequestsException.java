package com.eduvibe.exception;

import org.springframework.http.HttpStatus;

/**
 * Se ha superado el número de intentos permitido y hay que esperar: el bloqueo
 * temporal del inicio de sesión.
 */
public class TooManyRequestsException extends ApiException {

    public TooManyRequestsException(String mensaje) {
        super(HttpStatus.TOO_MANY_REQUESTS, mensaje);
    }
}
