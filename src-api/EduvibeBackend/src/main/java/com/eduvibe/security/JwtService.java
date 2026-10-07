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
    /** Versión de sesiones del usuario al emitir el token: ver User#invalidarSesiones. */
    private static final String CLAIM_VERSION = "tv";

    private final SecretKey clave;
    private final Duration validez;

    public JwtService(AppProperties propiedades, Environment entorno) {
        String secreto = propiedades.jwt().secret();
        if (secreto == null || secreto.isBlank()) {
            // En producción una clave aleatoria cerraría todas las sesiones en cada
            // reinicio (y no valdría con más de una instancia): mejor no arrancar.
            if (entorno.acceptsProfiles(Profiles.of("prod"))) {
                throw new IllegalStateException(
                        "JWT_SECRET es obligatorio con el perfil 'prod'. Genera uno con: openssl rand -base64 48");
            }
            this.clave = Jwts.SIG.HS256.key().build();
            LOG.warn("No hay JWT_SECRET configurado: se ha generado una clave aleatoria. "
                    + "Los tokens dejarán de ser válidos al reiniciar la aplicación.");
        } else {
            this.clave = Keys.hmacShaKeyFor(secreto.getBytes(StandardCharsets.UTF_8));
        }
        this.validez = Duration.ofHours(propiedades.jwt().expirationHours());
    }

    /**
     * Emite un token para el usuario indicado.
     *
     * El sujeto es el identificador, no el email: el email puede cambiar y el
     * identificador no, así que un token emitido sigue apuntando a la misma
     * persona aunque le corrijan la dirección.
     */
    public String emitirPara(User usuario) {
        Instant ahora = Instant.now();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(usuario.getId().toString())
                .claim(CLAIM_EMAIL, usuario.getEmail())
                .claim(CLAIM_NAME, usuario.getName())
                .claim(CLAIM_ROLE, usuario.getRole().getValor())
                .claim(CLAIM_ORG, usuario.getOrganization().getId().toString())
                .claim(CLAIM_VERSION, usuario.getTokenVersion())
                .issuedAt(Date.from(ahora))
                .expiration(Date.from(ahora.plus(validez)))
                .signWith(clave)
                .compact();
    }

    /** Cuándo caduca el token que se acaba de emitir. */
    public Instant caducidadDeUnTokenNuevo() {
        return Instant.now().plus(validez);
    }

    /**
     * Valida la firma y el plazo del token y reconstruye la identidad.
     *
     * @return la identidad, o null si el token no es utilizable. Se devuelve
     *         null en lugar de propagar la excepción porque un token inválido
     *         es una situación normal (caduca, o alguien prueba a mano), no un
     *         error del que haya que informar con una traza.
     */
    public AuthenticatedUser leer(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(clave)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            return new AuthenticatedUser(
                    UUID.fromString(claims.getSubject()),
                    claims.get(CLAIM_EMAIL, String.class),
                    claims.get(CLAIM_NAME, String.class),
                    UserRole.desdeValor(claims.get(CLAIM_ROLE, String.class)),
                    UUID.fromString(claims.get(CLAIM_ORG, String.class)),
                    new AuthenticatedUser.Sesion(
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
