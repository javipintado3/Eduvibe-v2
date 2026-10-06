package com.eduvibe.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Tokens de un solo uso (invitaciones, restablecer contraseña).
 *
 * Al usuario le llega el token; a la base de datos, solo su hash. Quien pueda
 * leer la base de datos no puede usar los enlaces pendientes.
 */
public final class Tokens {

    /** 32 bytes de entropía: suficiente para que el token no sea adivinable. */
    private static final int BYTES_DEL_TOKEN = 32;

    private static final SecureRandom ALEATORIO = new SecureRandom();

    private Tokens() {
    }

    public static String generar() {
        byte[] bytes = new byte[BYTES_DEL_TOKEN];
        ALEATORIO.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * SHA-256 basta aquí, a diferencia de con las contraseñas: el token tiene
     * 256 bits de entropía, así que no hay diccionario que probar y no hace
     * falta un algoritmo deliberadamente lento.
     */
    public static String hashear(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 debería estar disponible siempre", e);
        }
    }
}
