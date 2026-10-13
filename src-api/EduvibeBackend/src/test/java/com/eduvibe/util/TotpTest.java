package com.eduvibe.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TotpTest {

    /** "12345678901234567890" en Base32: el secreto de los vectores de prueba del RFC 6238. */
    private static final String SECRET_RFC = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";

    @Test
    @DisplayName("coincide con los vectores de prueba del RFC 6238 (últimos 6 dígitos)")
    void vectoresDelRfc() {
        assertThat(Totp.codeIn(SECRET_RFC, Instant.ofEpochSecond(59))).isEqualTo("287082");
        assertThat(Totp.codeIn(SECRET_RFC, Instant.ofEpochSecond(1111111109L))).isEqualTo("081804");
        assertThat(Totp.codeIn(SECRET_RFC, Instant.ofEpochSecond(1234567890L))).isEqualTo("005924");
        assertThat(Totp.codeIn(SECRET_RFC, Instant.ofEpochSecond(2000000000L))).isEqualTo("279037");
    }

    @Test
    @DisplayName("acepta el código del paso actual")
    void aceptaElActual() {
        Instant now = Instant.ofEpochSecond(1_700_000_000L);
        String code = Totp.codeIn(SECRET_RFC, now);

        assertThat(Totp.verify(SECRET_RFC, code, now)).isTrue();
    }

    @Test
    @DisplayName("tolera un paso de desfase de reloj, pero no dos")
    void toleraUnPasoDeDesfase() {
        Instant now = Instant.ofEpochSecond(1_700_000_000L);
        String ofPreviousStep = Totp.codeIn(SECRET_RFC, now.minusSeconds(30));
        String ofNextStep = Totp.codeIn(SECRET_RFC, now.plusSeconds(30));
        String fromTwoStepsAgo = Totp.codeIn(SECRET_RFC, now.minusSeconds(60));

        assertThat(Totp.verify(SECRET_RFC, ofPreviousStep, now)).isTrue();
        assertThat(Totp.verify(SECRET_RFC, ofNextStep, now)).isTrue();
        assertThat(Totp.verify(SECRET_RFC, fromTwoStepsAgo, now)).isFalse();
    }

    @Test
    @DisplayName("rechaza un código incorrecto, vacío o con formato imposible")
    void rechazaLoIncorrecto() {
        Instant now = Instant.ofEpochSecond(1_700_000_000L);
        String correct = Totp.codeIn(SECRET_RFC, now);
        String incorrect = correct.equals("000000") ? "000001" : "000000";

        assertThat(Totp.verify(SECRET_RFC, incorrect, now)).isFalse();
        assertThat(Totp.verify(SECRET_RFC, null, now)).isFalse();
        assertThat(Totp.verify(SECRET_RFC, "", now)).isFalse();
        assertThat(Totp.verify(SECRET_RFC, "12345", now)).isFalse();
        assertThat(Totp.verify(SECRET_RFC, "abcdef", now)).isFalse();
    }

    @Test
    @DisplayName("el secreto generado es Base32 de 160 bits y sirve para generar y verificar")
    void secretoGenerado() {
        String secret = Totp.generateSecret();

        assertThat(secret).hasSize(32).matches("[A-Z2-7]+");
        assertThat(Totp.generateSecret()).isNotEqualTo(secret);

        Instant now = Instant.now();
        assertThat(Totp.verify(secret, Totp.codeIn(secret, now), now)).isTrue();
    }

    @Test
    @DisplayName("el enlace otpauth lleva el secreto y escapa el nombre de la cuenta")
    void enlaceOtpauth() {
        String uri = Totp.uri("EduVibe", "ana+prueba@centro.es", SECRET_RFC);

        assertThat(uri).startsWith("otpauth://totp/EduVibe:ana%2Bprueba%40centro.es?");
        assertThat(uri).contains("secret=" + SECRET_RFC).contains("issuer=EduVibe")
                .contains("digits=6").contains("period=30");
    }
}
