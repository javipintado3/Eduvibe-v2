package com.eduvibe.security;

import java.time.Instant;
import java.util.UUID;

import com.eduvibe.model.enums.UserRole;

/**
 * Identidad de quien hace la petición, reconstruida a partir del token.
 *
 * Es lo que queda como principal en el contexto de seguridad, de modo que un
 * controlador o un servicio pueden saber quién es y de qué organización sin
 * volver a consultar la base de datos en cada petición.
 *
 * @param sesion datos del token concreto con el que se ha entrado, que hacen
 *               falta para poder revocarlo. Es null cuando la identidad no
 *               viene de un token (por ejemplo, en las pruebas).
 */
public record AuthenticatedUser(
        UUID id,
        String email,
        String name,
        UserRole role,
        UUID organizationId,
        Sesion sesion) {

    /**
     * @param id        identificador del token (jti); null en tokens anteriores a la revocación
     * @param version   versión de sesiones del usuario cuando se emitió el token
     * @param expiresAt cuándo caduca el token por sí solo
     */
    public record Sesion(UUID id, int version, Instant expiresAt) {
    }

    /** Identidad sin datos de token. */
    public AuthenticatedUser(UUID id, String email, String name, UserRole role, UUID organizationId) {
        this(id, email, name, role, organizationId, null);
    }

    public boolean esAdmin() {
        return role == UserRole.ADMIN;
    }
}
