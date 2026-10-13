package com.eduvibe.exception;

import org.springframework.http.HttpStatus;

/**
 * Se intentó cambiar algo de una cuenta de la demo pública que, si se tocara, la dejaría
 * inservible para el siguiente visitante (su contraseña, su verificación en dos pasos, darla
 * de baja...). Ver {@link com.eduvibe.service.ProtectedDemoAccounts}.
 */
public class ProtectedDemoAccountException extends ApiException {

    public ProtectedDemoAccountException(String message) {
        super(HttpStatus.FORBIDDEN, message);
    }
}
