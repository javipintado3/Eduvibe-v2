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

    private static final String ALFABETO_BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private static final SecureRandom ALEATORIO = new SecureRandom();

    /** 160 bits, el tamaño recomendado para HMAC-SHA1. */
    private static final int BYTES_DEL_SECRETO = 20;

    private static final int PASO_SEGUNDOS = 30;
    private static final int DIGITOS = 6;

    /**
     * Cuántos pasos de 30 s se aceptan a cada lado del actual. Con 1 se tolera
     * que el reloj del móvil vaya unos segundos adelantado o atrasado.
     */
    private static final int MARGEN_DE_PASOS = 1;

    private Totp() {
    }

    /** Secreto nuevo, en Base32 (lo que las apps de autenticación esperan). */
    public static String generarSecreto() {
        byte[] bytes = new byte[BYTES_DEL_SECRETO];
        ALEATORIO.nextBytes(bytes);
        return codificarBase32(bytes);
    }

    /** Código que corresponde al secreto en el instante indicado. */
    public static String codigoEn(String secretoBase32, Instant instante) {
        return codigo(secretoBase32, instante.getEpochSecond() / PASO_SEGUNDOS);
    }

    /** ¿Es válido el código ahora, tolerando un paso de desfase de reloj? */
    public static boolean verificar(String secretoBase32, String codigo, Instant ahora) {
        if (codigo == null || !codigo.matches("\\d{" + DIGITOS + "}")) {
            return false;
        }

        long pasoActual = ahora.getEpochSecond() / PASO_SEGUNDOS;
        boolean valido = false;

        // Se recorren siempre los tres pasos, sin cortar al acertar, para que el
        // tiempo de respuesta no dependa de cuál ha coincidido
        for (long paso = pasoActual - MARGEN_DE_PASOS; paso <= pasoActual + MARGEN_DE_PASOS; paso++) {
            boolean coincide = MessageDigest.isEqual(
                    codigo(secretoBase32, paso).getBytes(StandardCharsets.UTF_8),
                    codigo.getBytes(StandardCharsets.UTF_8));
            valido |= coincide;
        }
        return valido;
    }

    /** Enlace que las apps de autenticación leen desde el QR. */
    public static String uri(String emisor, String cuenta, String secretoBase32) {
        return "otpauth://totp/" + codificarUrl(emisor) + ":" + codificarUrl(cuenta)
                + "?secret=" + secretoBase32
                + "&issuer=" + codificarUrl(emisor)
                + "&algorithm=SHA1&digits=" + DIGITOS + "&period=" + PASO_SEGUNDOS;
    }

    /** HOTP (RFC 4226) sobre el contador de pasos de tiempo. */
    private static String codigo(String secretoBase32, long contador) {
        try {
            Mac hmac = Mac.getInstance("HmacSHA1");
            hmac.init(new SecretKeySpec(decodificarBase32(secretoBase32), "HmacSHA1"));
            byte[] hash = hmac.doFinal(ByteBuffer.allocate(Long.BYTES).putLong(contador).array());

            int desplazamiento = hash[hash.length - 1] & 0x0F;
            int binario = ((hash[desplazamiento] & 0x7F) << 24)
                    | ((hash[desplazamiento + 1] & 0xFF) << 16)
                    | ((hash[desplazamiento + 2] & 0xFF) << 8)
                    | (hash[desplazamiento + 3] & 0xFF);

            int modulo = (int) Math.pow(10, DIGITOS);
            return String.format("%0" + DIGITOS + "d", binario % modulo);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA1 debería estar disponible siempre", e);
        }
    }

    private static String codificarUrl(String texto) {
        return URLEncoder.encode(texto, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String codificarBase32(byte[] datos) {
        StringBuilder resultado = new StringBuilder();
        int buffer = 0;
        int bitsEnBuffer = 0;

        for (byte dato : datos) {
            buffer = (buffer << 8) | (dato & 0xFF);
            bitsEnBuffer += 8;
            while (bitsEnBuffer >= 5) {
                resultado.append(ALFABETO_BASE32.charAt((buffer >> (bitsEnBuffer - 5)) & 0x1F));
                bitsEnBuffer -= 5;
            }
        }
        if (bitsEnBuffer > 0) {
            resultado.append(ALFABETO_BASE32.charAt((buffer << (5 - bitsEnBuffer)) & 0x1F));
        }
        return resultado.toString();
    }

    private static byte[] decodificarBase32(String texto) {
        String limpio = texto.toUpperCase().replace("=", "").replace(" ", "");
        ByteBuffer salida = ByteBuffer.allocate(limpio.length() * 5 / 8);
        int buffer = 0;
        int bitsEnBuffer = 0;

        for (char caracter : limpio.toCharArray()) {
            int valor = ALFABETO_BASE32.indexOf(caracter);
            if (valor < 0) {
                throw new IllegalArgumentException("Carácter no válido en Base32: " + caracter);
            }
            buffer = (buffer << 5) | valor;
            bitsEnBuffer += 5;
            if (bitsEnBuffer >= 8) {
                salida.put((byte) ((buffer >> (bitsEnBuffer - 8)) & 0xFF));
                bitsEnBuffer -= 8;
            }
        }
        return salida.array();
    }
}
