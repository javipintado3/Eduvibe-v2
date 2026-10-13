package com.eduvibe.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;

import com.eduvibe.config.AppProperties;
import com.eduvibe.model.User;
import com.eduvibe.model.enums.UserRole;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Emisión y lectura de los tokens de acceso.
 *
 * A diferencia de la versión anterior, la clave no está escrita en el código
 * sino que llega por configuración, los métodos no son estáticos (así la clase
 * se puede inyectar y sustituir en pruebas) y el token se devuelve limpio, sin
 * el prefijo "Bearer ": ese prefijo pertenece a la cabecera HTTP, no al token.
 */
@Service
public class JwtService {

    private static final Logger LOG = LoggerFactory.getLogger(JwtService.class);

    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_NAME = "name";
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_ORG = "org";
    /** Versión de sesiones del usuario al emitir el token: ver User#invalidateSessions. */
    private static final String CLAIM_VERSION = "tv";

    private final SecretKey key;
    private final Duration validity;

    public JwtService(AppProperties properties, Environment environment) {
        String secret = properties.jwt().secret();
        if (secret == null || secret.isBlank()) {
            // En producción una clave aleatoria cerraría todas las sesiones en cada
            // reinicio (y no valdría con más de una instancia): mejor no arrancar.
            if (environment.acceptsProfiles(Profiles.of("prod"))) {
                throw new IllegalStateException(
                        "JWT_SECRET es obligatorio con el perfil 'prod'. Genera uno con: openssl rand -base64 48");
            }
            this.key = Jwts.SIG.HS256.key().build();
            LOG.warn("No hay JWT_SECRET configurado: se ha generado una clave aleatoria. "
                    + "Los tokens dejarán de ser válidos al reiniciar la aplicación.");
        } else {
            this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        }
        this.validity = properties.jwt().expirationMinutes() > 0
                ? Duration.ofMinutes(properties.jwt().expirationMinutes())
                : Duration.ofHours(properties.jwt().expirationHours());
    }

    /**
     * Emite un token para el usuario indicado.
     *
     * El sujeto es el identificador, no el email: el email puede cambiar y el
     * identificador no, así que un token emitido sigue apuntando a la misma
     * persona aunque le corrijan la dirección.
     */
    public String issueFor(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(user.getId().toString())
                .claim(CLAIM_EMAIL, user.getEmail())
                .claim(CLAIM_NAME, user.getName())
                .claim(CLAIM_ROLE, user.getRole().getValue())
                .claim(CLAIM_ORG, user.getOrganization().getId().toString())
                .claim(CLAIM_VERSION, user.getTokenVersion())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(validity)))
                .signWith(key)
                .compact();
    }

    /** Cuándo caduca el token que se acaba de emitir. */
    public Instant expiryOfTokenNew() {
        return Instant.now().plus(validity);
    }

    /**
     * Valida la firma y el plazo del token y reconstruye la identidad.
     *
     * @return la identidad, o null si el token no es utilizable. Se devuelve
     *         null en lugar de propagar la excepción porque un token inválido
     *         es una situación normal (caduca, o alguien prueba a mano), no un
     *         error del que haya que informar con una traza.
     */
    public AuthenticatedUser read(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            return new AuthenticatedUser(
                    UUID.fromString(claims.getSubject()),
                    claims.get(CLAIM_EMAIL, String.class),
                    claims.get(CLAIM_NAME, String.class),
                    UserRole.fromValue(claims.get(CLAIM_ROLE, String.class)),
                    UUID.fromString(claims.get(CLAIM_ORG, String.class)),
                    new AuthenticatedUser.Session(
                            // Los tokens emitidos antes de existir la revocación no traen id ni
                            // versión: se leen como "sin id" y versión 0 y siguen valiendo hasta caducar
                            claims.getId() == null ? null : UUID.fromString(claims.getId()),
                            claims.get(CLAIM_VERSION, Integer.class) == null ? 0 : claims.get(CLAIM_VERSION, Integer.class),
                            claims.getExpiration().toInstant()));

        } catch (JwtException | IllegalArgumentException e) {
            LOG.debug("Token rechazado: {}", e.getMessage());
            return null;
        }
    }
}
