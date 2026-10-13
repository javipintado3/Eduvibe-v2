package com.eduvibe.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.eduvibe.config.AppProperties;
import com.eduvibe.model.LoginAttempt;
import com.eduvibe.repository.LoginAttemptRepository;

@ExtendWith(MockitoExtension.class)
class LoginAttemptServiceTest {

    private static final String EMAIL = "ana@centro.es";

    @Mock
    private LoginAttemptRepository repository;

    private LoginAttemptService service;

    @BeforeEach
    void crearServicio() {
        AppProperties properties = new AppProperties(null, null, null, null,
                new AppProperties.Login(5, 15), null, null);
        service = new LoginAttemptService(repository, properties);
    }

    @Test
    @DisplayName("no bloquea con menos fallos que el máximo")
    void pocosFallos() {
        when(repository.countByEmailAndSucceededFalseAndAttemptedAtAfter(eq(EMAIL), any(Instant.class)))
                .thenReturn(4L);

        assertThat(service.isLocked(EMAIL)).isFalse();
    }

    @Test
    @DisplayName("bloquea al llegar al máximo de fallos")
    void maximoDeFallos() {
        when(repository.countByEmailAndSucceededFalseAndAttemptedAtAfter(eq(EMAIL), any(Instant.class)))
                .thenReturn(5L);

        assertThat(service.isLocked(EMAIL)).isTrue();
    }

    @Test
    @DisplayName("solo cuenta los fallos de la ventana de bloqueo")
    void soloCuentaLaVentana() {
        service.isLocked(EMAIL);

        ArgumentCaptor<Instant> from = ArgumentCaptor.forClass(Instant.class);
        verify(repository).countByEmailAndSucceededFalseAndAttemptedAtAfter(eq(EMAIL), from.capture());

        Instant expected = Instant.now().minusSeconds(15 * 60);
        assertThat(from.getValue()).isBetween(expected.minusSeconds(5), expected.plusSeconds(5));
    }

    @Test
    @DisplayName("registra un fallo con el email y la IP")
    void registraElFallo() {
        service.recordFailure(EMAIL, "10.0.0.1");

        ArgumentCaptor<LoginAttempt> attempt = ArgumentCaptor.forClass(LoginAttempt.class);
        verify(repository).save(attempt.capture());
        assertThat(attempt.getValue().getEmail()).isEqualTo(EMAIL);
        assertThat(attempt.getValue().getIpAddress()).isEqualTo("10.0.0.1");
        assertThat(attempt.getValue().isSucceeded()).isFalse();
    }

    @Test
    @DisplayName("un acceso correcto borra los fallos previos y se apunta a sí mismo")
    void exitoReiniciaElContador() {
        service.recordSuccess(EMAIL, "10.0.0.1");

        verify(repository).deleteFailuresByEmail(EMAIL);
        ArgumentCaptor<LoginAttempt> attempt = ArgumentCaptor.forClass(LoginAttempt.class);
        verify(repository).save(attempt.capture());
        assertThat(attempt.getValue().isSucceeded()).isTrue();
    }

    @Test
    @DisplayName("la purga borra lo anterior a 90 días")
    void purgaPorRetencion() {
        when(repository.deleteOlderThan(any(Instant.class))).thenReturn(3);

        service.purgeOld();

        ArgumentCaptor<Instant> limit = ArgumentCaptor.forClass(Instant.class);
        verify(repository).deleteOlderThan(limit.capture());
        Instant expected = Instant.now().minusSeconds(90L * 24 * 3600);
        assertThat(limit.getValue()).isBetween(expected.minusSeconds(5), expected.plusSeconds(5));
    }
}
