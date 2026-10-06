package com.eduvibe.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TotpTest {

    /** "12345678901234567890" en Base32: el secreto de los vectores de prueba del RFC 6238. */
    private static final String SECRETO_RFC = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";

    @Test
    @DisplayName("coincide con los vectores de prueba del RFC 6238 (últimos 6 dígitos)")
    void vectoresDelRfc() {
        assertThat(Totp.codigoEn(SECRETO_RFC, Instant.ofEpochSecond(59))).isEqualTo("287082");
        assertThat(Totp.codigoEn(SECRETO_RFC, Instant.ofEpochSecond(1111111109L))).isEqualTo("081804");
        assertThat(Totp.codigoEn(SECRETO_RFC, Instant.ofEpochSecond(1234567890L))).isEqualTo("005924");
        assertThat(Totp.codigoEn(SECRETO_RFC, Instant.ofEpochSecond(2000000000L))).isEqualTo("279037");
    }

    @Test
    @DisplayName("acepta el código del paso actual")
    void aceptaElActual() {
        Instant ahora = Instant.ofEpochSecond(1_700_000_000L);
        String codigo = Totp.codigoEn(SECRETO_RFC, ahora);

        assertThat(Totp.verificar(SECRETO_RFC, codigo, ahora)).isTrue();
    }

    @Test
    @DisplayName("tolera un paso de desfase de reloj, pero no dos")
    void toleraUnPasoDeDesfase() {
        Instant ahora = Instant.ofEpochSecond(1_700_000_000L);
        String delPasoAnterior = Totp.codigoEn(SECRETO_RFC, ahora.minusSeconds(30));
        String delPasoSiguiente = Totp.codigoEn(SECRETO_RFC, ahora.plusSeconds(30));
        String deHaceDosPasos = Totp.codigoEn(SECRETO_RFC, ahora.minusSeconds(60));

        assertThat(Totp.verificar(SECRETO_RFC, delPasoAnterior, ahora)).isTrue();
        assertThat(Totp.verificar(SECRETO_RFC, delPasoSiguiente, ahora)).isTrue();
        assertThat(Totp.verificar(SECRETO_RFC, deHaceDosPasos, ahora)).isFalse();
    }

    @Test
    @DisplayName("rechaza un código incorrecto, vacío o con formato imposible")
    void rechazaLoIncorrecto() {
        Instant ahora = Instant.ofEpochSecond(1_700_000_000L);
        String correcto = Totp.codigoEn(SECRETO_RFC, ahora);
        String incorrecto = correcto.equals("000000") ? "000001" : "000000";

        assertThat(Totp.verificar(SECRETO_RFC, incorrecto, ahora)).isFalse();
        assertThat(Totp.verificar(SECRETO_RFC, null, ahora)).isFalse();
        assertThat(Totp.verificar(SECRETO_RFC, "", ahora)).isFalse();
        assertThat(Totp.verificar(SECRETO_RFC, "12345", ahora)).isFalse();
        assertThat(Totp.verificar(SECRETO_RFC, "abcdef", ahora)).isFalse();
    }

    @Test
    @DisplayName("el secreto generado es Base32 de 160 bits y sirve para generar y verificar")
    void secretoGenerado() {
        String secreto = Totp.generarSecreto();

        assertThat(secreto).hasSize(32).matches("[A-Z2-7]+");
        assertThat(Totp.generarSecreto()).isNotEqualTo(secreto);

        Instant ahora = Instant.now();
        assertThat(Totp.verificar(secreto, Totp.codigoEn(secreto, ahora), ahora)).isTrue();
    }

    @Test
    @DisplayName("el enlace otpauth lleva el secreto y escapa el nombre de la cuenta")
    void enlaceOtpauth() {
        String uri = Totp.uri("EduVibe", "ana+prueba@centro.es", SECRETO_RFC);

        assertThat(uri).startsWith("otpauth://totp/EduVibe:ana%2Bprueba%40centro.es?");
        assertThat(uri).contains("secret=" + SECRETO_RFC).contains("issuer=EduVibe")
                .contains("digits=6").contains("period=30");
    }
}
