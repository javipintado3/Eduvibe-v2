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
 * @param registration solicitudes de registro públicas: plazos y límites anti-abuso
 * @param demo       cuentas de demostración públicas que solo pueden leer
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Cors cors,
        Jwt jwt,
        String frontendUrl,
        Invitation invitation,
        Login login,
        PasswordReset passwordReset,
        Registration registration,
        Demo demo) {

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

    /**
     * @param verificationExpirationHours horas que vale el enlace para confirmar el correo
     * @param maxPerIpPerHour             solicitudes que puede hacer una misma IP en una hora
     * @param maxPerHour                  tope de solicitudes de toda la plataforma en una hora;
     *                                    freno de último recurso contra una inundación repartida
     *                                    entre muchas IP
     * @param trustProxy                  si la API está detrás de un proxy propio (nginx) y la IP
     *                                    real del cliente viene en X-Forwarded-For. Con false esa
     *                                    cabecera se ignora, porque cualquiera podría falsearla
     *                                    para esquivar el límite por IP
     */
    public record Registration(long verificationExpirationHours, int maxPerIpPerHour, int maxPerHour,
                               boolean trustProxy) {
    }

    /**
     * Las credenciales de la demo pública están en el README, así que cualquiera puede
     * entrar con ellas. Para que nadie pueda escribir (ni dejar mensajes ofensivos que
     * vea el siguiente visitante), las cuentas de este dominio solo pueden consultar.
     *
     * @param readOnlyEmailDomain dominio de los correos de demostración (por ejemplo
     *                            "eduvibe.demo"). Vacío o ausente: la función está apagada
     *                            y todas las cuentas pueden escribir, como en desarrollo
     */
    public record Demo(String readOnlyEmailDomain) {

        /** Si el correo es de una cuenta de demostración de solo lectura. */
        public boolean esSoloLectura(String email) {
            if (readOnlyEmailDomain == null || readOnlyEmailDomain.isBlank() || email == null) {
                return false;
            }
            return email.toLowerCase().endsWith("@" + readOnlyEmailDomain.trim().toLowerCase());
        }
    }
}
