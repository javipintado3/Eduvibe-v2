package com.eduvibe.exception;

import org.springframework.http.HttpStatus;

/**
 * La contraseña es correcta pero la cuenta está desactivada.
 *
 * Se responde 403 y no 401 a propósito: el 401 es "credenciales inválidas" (y
 * hace que el frontend cierre la sesión), y aquí las credenciales son buenas.
 * Solo se lanza con la contraseña acertada: decir "desactivada" a quien no la
 * sabe delataría qué cuentas existen.
 */
public class AccountDisabledException extends ApiException {

    public AccountDisabledException() {
        super(HttpStatus.FORBIDDEN,
                "Tu cuenta está desactivada. Ponte en contacto con la administración de tu centro.");
    }
}
