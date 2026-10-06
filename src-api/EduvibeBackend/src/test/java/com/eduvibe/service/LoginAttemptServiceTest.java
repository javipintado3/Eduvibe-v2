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

    private LoginAttemptService servicio;

    @BeforeEach
    void crearServicio() {
        AppProperties propiedades = new AppProperties(null, null, null, null,
                new AppProperties.Login(5, 15));
        servicio = new LoginAttemptService(repository, propiedades);
    }

    @Test
    @DisplayName("no bloquea con menos fallos que el máximo")
    void pocosFallos() {
        when(repository.countByEmailAndSucceededFalseAndAttemptedAtAfter(eq(EMAIL), any(Instant.class)))
                .thenReturn(4L);

        assertThat(servicio.estaBloqueado(EMAIL)).isFalse();
    }

    @Test
    @DisplayName("bloquea al llegar al máximo de fallos")
    void maximoDeFallos() {
        when(repository.countByEmailAndSucceededFalseAndAttemptedAtAfter(eq(EMAIL), any(Instant.class)))
                .thenReturn(5L);

        assertThat(servicio.estaBloqueado(EMAIL)).isTrue();
    }

    @Test
    @DisplayName("solo cuenta los fallos de la ventana de bloqueo")
    void soloCuentaLaVentana() {
        servicio.estaBloqueado(EMAIL);

        ArgumentCaptor<Instant> desde = ArgumentCaptor.forClass(Instant.class);
        verify(repository).countByEmailAndSucceededFalseAndAttemptedAtAfter(eq(EMAIL), desde.capture());

        Instant esperado = Instant.now().minusSeconds(15 * 60);
        assertThat(desde.getValue()).isBetween(esperado.minusSeconds(5), esperado.plusSeconds(5));
    }

    @Test
    @DisplayName("registra un fallo con el email y la IP")
    void registraElFallo() {
        servicio.registrarFallo(EMAIL, "10.0.0.1");

        ArgumentCaptor<LoginAttempt> intento = ArgumentCaptor.forClass(LoginAttempt.class);
        verify(repository).save(intento.capture());
        assertThat(intento.getValue().getEmail()).isEqualTo(EMAIL);
        assertThat(intento.getValue().getIpAddress()).isEqualTo("10.0.0.1");
        assertThat(intento.getValue().isSucceeded()).isFalse();
    }

    @Test
    @DisplayName("un acceso correcto borra los fallos previos y se apunta a sí mismo")
    void exitoReiniciaElContador() {
        servicio.registrarExito(EMAIL, "10.0.0.1");

        verify(repository).borrarFallosDe(EMAIL);
        ArgumentCaptor<LoginAttempt> intento = ArgumentCaptor.forClass(LoginAttempt.class);
        verify(repository).save(intento.capture());
        assertThat(intento.getValue().isSucceeded()).isTrue();
    }

    @Test
    @DisplayName("la purga borra lo anterior a 90 días")
    void purgaPorRetencion() {
        when(repository.borrarAnterioresA(any(Instant.class))).thenReturn(3);

        servicio.purgarAntiguos();

        ArgumentCaptor<Instant> limite = ArgumentCaptor.forClass(Instant.class);
        verify(repository).borrarAnterioresA(limite.capture());
        Instant esperado = Instant.now().minusSeconds(90L * 24 * 3600);
        assertThat(limite.getValue()).isBetween(esperado.minusSeconds(5), esperado.plusSeconds(5));
    }
}
