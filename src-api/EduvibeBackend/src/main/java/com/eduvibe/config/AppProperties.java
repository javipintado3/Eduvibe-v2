package com.eduvibe.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuración propia de la aplicación, enlazada desde application.properties.
 *
 * Tenerla en un objeto tipado, en lugar de esparcir @Value por las clases,
 * hace que un valor mal escrito falle al arrancar y no a mitad de una petición,
 * y deja en un solo sitio la lista de lo que se puede configurar.
 *
 * @param cors       orígenes autorizados para el navegador
 * @param jwt        firma y validez de los tokens
 * @param frontendUrl URL pública del frontend, para componer los enlaces de invitación
 * @param invitation plazo de las invitaciones de alta
 * @param login      límite de intentos de inicio de sesión
 * @param passwordReset plazo de los enlaces de "olvidé mi contraseña"
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Cors cors,
        Jwt jwt,
        String frontendUrl,
        Invitation invitation,
        Login login,
        PasswordReset passwordReset) {

    public record Cors(List<String> allowedOrigins) {
    }

    /**
     * @param secret          clave de firma; si viene vacía se genera una aleatoria al arrancar
     * @param expirationHours horas que vale un token
     */
    public record Jwt(String secret, long expirationHours) {
    }

    public record Invitation(long expirationHours) {
    }

    /**
     * @param maxFailedAttempts fallos seguidos de un email que provocan el bloqueo
     * @param lockoutMinutes    minutos que dura el bloqueo
     */
    public record Login(int maxFailedAttempts, long lockoutMinutes) {
    }

    /** @param expirationMinutes minutos que vale un enlace de "olvidé mi contraseña" */
    public record PasswordReset(long expirationMinutes) {
    }
}
