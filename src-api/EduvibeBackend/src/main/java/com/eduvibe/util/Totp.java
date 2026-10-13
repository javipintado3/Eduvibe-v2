package com.eduvibe.util;

import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Códigos de un solo uso basados en el tiempo (TOTP, RFC 6238), los de 6 dígitos
 * que cambian cada 30 segundos en Google Authenticator, Authy y similares.
 *
 * Se implementa aquí porque son pocas líneas y así no se añade una dependencia
 * para algo tan acotado. Se comprueba contra los vectores de prueba del propio RFC.
 */
public final class Totp {

    private static final String ALPHABET_BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private static final SecureRandom RANDOM = new SecureRandom();

    /** 160 bits, el tamaño recomendado para HMAC-SHA1. */
    private static final int SECRET_BYTES = 20;

    private static final int STEP_SECONDS = 30;
    private static final int DIGITS = 6;

    /**
     * Cuántos pasos de 30 s se aceptan a cada lado del actual. Con 1 se tolera
     * que el reloj del móvil vaya unos segundos adelantado o atrasado.
     */
    private static final int STEP_MARGIN = 1;

    private Totp() {
    }

    /** Secreto nuevo, en Base32 (lo que las apps de autenticación esperan). */
    public static String generateSecret() {
        byte[] bytes = new byte[SECRET_BYTES];
        RANDOM.nextBytes(bytes);
        return encodeBase32(bytes);
    }

    /** Código que corresponde al secreto en el instante indicado. */
    public static String codeIn(String secretBase32, Instant instant) {
        return code(secretBase32, instant.getEpochSecond() / STEP_SECONDS);
    }

    /** ¿Es válido el código ahora, tolerando un paso de desfase de reloj? */
    public static boolean verify(String secretBase32, String code, Instant now) {
        if (code == null || !code.matches("\\d{" + DIGITS + "}")) {
            return false;
        }

        long currentStep = now.getEpochSecond() / STEP_SECONDS;
        boolean valid = false;

        // Se recorren siempre los tres pasos, sin cortar al acertar, para que el
        // tiempo de respuesta no dependa de cuál ha coincidido
        for (long step = currentStep - STEP_MARGIN; step <= currentStep + STEP_MARGIN; step++) {
            boolean matches = MessageDigest.isEqual(
                    code(secretBase32, step).getBytes(StandardCharsets.UTF_8),
                    code.getBytes(StandardCharsets.UTF_8));
            valid |= matches;
        }
        return valid;
    }

    /** Enlace que las apps de autenticación leen desde el QR. */
    public static String uri(String issuer, String account, String secretBase32) {
        return "otpauth://totp/" + encodeUrl(issuer) + ":" + encodeUrl(account)
                + "?secret=" + secretBase32
                + "&issuer=" + encodeUrl(issuer)
                + "&algorithm=SHA1&digits=" + DIGITS + "&period=" + STEP_SECONDS;
    }

    /** HOTP (RFC 4226) sobre el contador de pasos de tiempo. */
    private static String code(String secretBase32, long counter) {
        try {
            Mac hmac = Mac.getInstance("HmacSHA1");
            hmac.init(new SecretKeySpec(decodeBase32(secretBase32), "HmacSHA1"));
            byte[] hash = hmac.doFinal(ByteBuffer.allocate(Long.BYTES).putLong(counter).array());

            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);

            int module = (int) Math.pow(10, DIGITS);
            return String.format("%0" + DIGITS + "d", binary % module);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA1 debería estar disponible siempre", e);
        }
    }

    private static String encodeUrl(String texto) {
        return URLEncoder.encode(texto, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String encodeBase32(byte[] data) {
        StringBuilder result = new StringBuilder();
        int buffer = 0;
        int bitsInBuffer = 0;

        for (byte current : data) {
            buffer = (buffer << 8) | (current & 0xFF);
            bitsInBuffer += 8;
            while (bitsInBuffer >= 5) {
                result.append(ALPHABET_BASE32.charAt((buffer >> (bitsInBuffer - 5)) & 0x1F));
                bitsInBuffer -= 5;
            }
        }
        if (bitsInBuffer > 0) {
            result.append(ALPHABET_BASE32.charAt((buffer << (5 - bitsInBuffer)) & 0x1F));
        }
        return result.toString();
    }

    private static byte[] decodeBase32(String texto) {
        String clean = texto.toUpperCase().replace("=", "").replace(" ", "");
        ByteBuffer output = ByteBuffer.allocate(clean.length() * 5 / 8);
        int buffer = 0;
        int bitsInBuffer = 0;

        for (char character : clean.toCharArray()) {
            int value = ALPHABET_BASE32.indexOf(character);
            if (value < 0) {
                throw new IllegalArgumentException("Carácter no válido en Base32: " + character);
            }
            buffer = (buffer << 5) | value;
            bitsInBuffer += 5;
            if (bitsInBuffer >= 8) {
                output.put((byte) ((buffer >> (bitsInBuffer - 8)) & 0xFF));
                bitsInBuffer -= 8;
            }
        }
        return output.array();
    }
}
