package com.eduvibe.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

class DemoResetJobTest {

    private static final String CON_SEMILLA = "classpath:db/migration,classpath:db/demo";
    private static final String SIN_SEMILLA = "classpath:db/migration";

    private Flyway flyway;
    private JdbcTemplate jdbc;

    @BeforeEach
    void preparar() {
        flyway = mock(Flyway.class);
        jdbc = mock(JdbcTemplate.class);
    }

    /** Simula lo que devuelve la base de datos: true = hace más de las horas indicadas. */
    @SuppressWarnings("unchecked")
    private void ultimoReinicio(Boolean hechoHaceMucho) {
        when(jdbc.query(anyString(), any(ResultSetExtractor.class), eq(12))).thenReturn(hechoHaceMucho);
    }

    @Nested
    @DisplayName("A la hora del cron")
    class ACronometro {

        @Test
        @DisplayName("borra y vuelve a crear todo, en ese orden")
        void reinicia() {
            new DemoResetJob(flyway, jdbc, CON_SEMILLA, 12).restablecer();

            InOrder orden = inOrder(flyway);
            orden.verify(flyway).clean();
            orden.verify(flyway).migrate();
        }

        @Test
        @DisplayName("se niega si no hay semilla de demo: esa base de datos no es una demo")
        void sinSemillaNoTocaNada() {
            new DemoResetJob(flyway, jdbc, SIN_SEMILLA, 12).restablecer();

            verifyNoInteractions(flyway);
        }
    }

    @Nested
    @DisplayName("Al arrancar")
    class AlArrancar {

        @Test
        @DisplayName("reinicia si hace más de las horas indicadas del último reinicio")
        void reiniciaSiHaceMucho() {
            ultimoReinicio(true);

            new DemoResetJob(flyway, jdbc, CON_SEMILLA, 12).restablecerSiHaceMuchoQueNoSeHizo();

            verify(flyway).clean();
            verify(flyway).migrate();
        }

        @Test
        @DisplayName("no toca nada si el último reinicio es reciente")
        void noReiniciaSiEsReciente() {
            ultimoReinicio(false);

            new DemoResetJob(flyway, jdbc, CON_SEMILLA, 12).restablecerSiHaceMuchoQueNoSeHizo();

            verify(flyway, never()).clean();
        }

        @Test
        @DisplayName("si no hay rastro de la primera migración, prefiere no borrar")
        void sinRastroNoBorra() {
            ultimoReinicio(null);

            new DemoResetJob(flyway, jdbc, CON_SEMILLA, 12).restablecerSiHaceMuchoQueNoSeHizo();

            verify(flyway, never()).clean();
        }

        @Test
        @DisplayName("con 0 horas la función está apagada y ni siquiera consulta la base de datos")
        void apagadoConCero() {
            new DemoResetJob(flyway, jdbc, CON_SEMILLA, 0).restablecerSiHaceMuchoQueNoSeHizo();

            verifyNoInteractions(flyway, jdbc);
        }

        @Test
        @DisplayName("se niega si no hay semilla de demo, aunque haga mucho del último reinicio")
        void sinSemillaNoBorra() {
            new DemoResetJob(flyway, jdbc, SIN_SEMILLA, 12).restablecerSiHaceMuchoQueNoSeHizo();

            verifyNoInteractions(flyway);
        }
    }
}
