package com.eduvibe.config;

import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Devuelve la demo pública a su estado inicial cada cierto tiempo.
 *
 * Las credenciales de la demo están en el README y cualquiera puede entrar y escribir. Lo
 * que alguien deje (un mensaje en un foro, un usuario inventado) desaparece solo.
 *
 * Funciona borrando todo el esquema y volviendo a aplicar las migraciones, incluida la
 * semilla de db/demo. Por eso es peligrosa y está pensada con varios candados:
 *
 *  - Solo existe si se configura app.demo.reset-cron (DEMO_RESET_CRON). Vacío = no hay
 *    tarea, y en ese caso Flyway tampoco permite borrar (su "clean" sigue desactivado).
 *  - Se niega a ejecutarse si la semilla db/demo no está entre las ubicaciones de Flyway:
 *    esa semilla es la señal de que esta base de datos es una demo y no una de verdad.
 *
 * Se dispara de dos formas:
 *
 *  - A la hora del cron. No basta por sí sola: en el alojamiento gratuito la API se duerme
 *    sin visitas, y una API dormida no ejecuta tareas programadas.
 *  - Al arrancar, si el último reinicio fue hace más de app.demo.reset-after-hours horas.
 *    Así quien despierta la API siempre ve la demo limpia, aunque nadie la haya tenido
 *    despierta a la hora del cron.
 *
 * El momento del último reinicio se lee de la propia base de datos: la entrada de Flyway
 * de la primera migración se vuelve a escribir cada vez que se borra y se recrea todo, así
 * que su fecha es la del último reinicio y no hace falta guardarla en ningún sitio. Un
 * despliegue normal (que solo añade migraciones nuevas) no la toca.
 *
 * Los archivos subidos (carpeta de uploads) no se tocan.
 */
@Component
@ConditionalOnExpression("!'${app.demo.reset-cron:}'.isBlank()")
public class DemoResetJob {

    private static final Logger LOG = LoggerFactory.getLogger(DemoResetJob.class);

    private final Flyway flyway;
    private final JdbcTemplate jdbc;
    private final String flywayLocations;
    private final int hoursUntilReset;

    public DemoResetJob(Flyway flyway, JdbcTemplate jdbc,
                        @Value("${spring.flyway.locations}") String flywayLocations,
                        @Value("${app.demo.reset-after-hours:12}") int hoursUntilReset) {
        this.flyway = flyway;
        this.jdbc = jdbc;
        this.flywayLocations = flywayLocations;
        this.hoursUntilReset = hoursUntilReset;
    }

    @Scheduled(cron = "${app.demo.reset-cron}", zone = "${app.demo.reset-zone:Europe/Madrid}")
    public void resetOnSchedule() {
        if (!isDemo()) {
            return;
        }
        reset();
    }

    /** Al arrancar: si la demo lleva demasiado sin reiniciarse, se reinicia antes de enseñarla. */
    @EventListener(ApplicationReadyEvent.class)
    public void resetIfNotResetRecently() {
        if (!isDemo() || hoursUntilReset <= 0 || !isOverdueForReset()) {
            return;
        }
        LOG.info("La demo lleva más de {} h sin reiniciarse", hoursUntilReset);
        reset();
    }

    private boolean isDemo() {
        if (!flywayLocations.contains("db/demo")) {
            LOG.error("Reinicio de la demo cancelado: la semilla db/demo no está cargada, "
                    + "así que esta base de datos no se considera una demo.");
            return false;
        }
        return true;
    }

    /** Sin entrada de la primera migración (no debería pasar) se prefiere no tocar nada. */
    private boolean isOverdueForReset() {
        Boolean tooMuch = jdbc.query(
                "SELECT (now() - installed_on) > make_interval(hours => ?) "
                        + "FROM flyway_schema_history WHERE version = '1'",
                rs -> rs.next() ? rs.getBoolean(1) : null,
                hoursUntilReset);
        return Boolean.TRUE.equals(tooMuch);
    }

    private void reset() {
        LOG.info("Reiniciando la demo: se borran los datos y se vuelve a cargar la semilla");
        flyway.clean();
        flyway.migrate();
        LOG.info("Demo reiniciada");
    }

    /** Flyway trae el borrado prohibido; solo se permite cuando esta tarea está activada. */
    @Bean
    static FlywayConfigurationCustomizer allowDemoReset() {
        return configuration -> configuration.cleanDisabled(false);
    }
}
