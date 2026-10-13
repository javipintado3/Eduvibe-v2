package com.eduvibe.config;

import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * Devuelve la demo pública a su estado inicial cada cierto tiempo.
 *
 * Las credenciales de la demo están en el README y cualquiera puede entrar. Aunque las
 * cuentas demo solo pueden consultar, esta tarea es la red de seguridad: si alguien
 * consigue dejar algo (una solicitud de registro, un dato raro), desaparece sola en la
 * siguiente pasada.
 *
 * Funciona borrando todo el esquema y volviendo a aplicar las migraciones, incluida la
 * semilla de db/demo. Por eso es peligrosa y está pensada con varios candados:
 *
 *  - Solo existe si se configura app.demo.reset-cron (DEMO_RESET_CRON). Vacío = no hay
 *    tarea, y en ese caso Flyway tampoco permite borrar (su "clean" sigue desactivado).
 *  - Se niega a ejecutarse si no hay dominio de demostración configurado, que es la señal
 *    de que esta base de datos es una demo y no una de verdad.
 *
 * Los archivos subidos (carpeta de uploads) no se tocan: en la demo las cuentas no pueden
 * subir nada.
 */
@Component
@ConditionalOnExpression("!'${app.demo.reset-cron:}'.isBlank()")
@RequiredArgsConstructor
public class DemoResetJob {

    private static final Logger LOG = LoggerFactory.getLogger(DemoResetJob.class);

    private final Flyway flyway;
    private final AppProperties propiedades;

    @Scheduled(cron = "${app.demo.reset-cron}", zone = "${app.demo.reset-zone:Europe/Madrid}")
    public void restablecer() {
        if (propiedades.demo() == null || propiedades.demo().readOnlyEmailDomain() == null
                || propiedades.demo().readOnlyEmailDomain().isBlank()) {
            LOG.error("Reinicio de la demo cancelado: no hay dominio de demostración configurado, "
                    + "así que esta base de datos no se considera una demo.");
            return;
        }

        LOG.info("Reiniciando la demo: se borran los datos y se vuelve a cargar la semilla");
        flyway.clean();
        flyway.migrate();
        LOG.info("Demo reiniciada");
    }

    /** Flyway trae el borrado prohibido; solo se permite cuando esta tarea está activada. */
    @Bean
    static FlywayConfigurationCustomizer permitirBorradoDeLaDemo() {
        return configuracion -> configuracion.cleanDisabled(false);
    }
}
