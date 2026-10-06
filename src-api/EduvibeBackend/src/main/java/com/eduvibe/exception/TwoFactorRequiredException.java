package com.eduvibe.exception;

import org.springframework.http.HttpStatus;

/**
 * La contraseña es correcta pero la cuenta tiene la verificación en dos pasos
 * activada y falta el código. Es un 428 (Precondition Required) y no un 401,
 * para que el frontend sepa que debe pedir el código y no que las credenciales
 * han fallado.
 */
public class TwoFactorRequiredException extends ApiException {

    public TwoFactorRequiredException() {
        super(HttpStatus.PRECONDITION_REQUIRED, "Introduce el código de verificación");
    }
}
