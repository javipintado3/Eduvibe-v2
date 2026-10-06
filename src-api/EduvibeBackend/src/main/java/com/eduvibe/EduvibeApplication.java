package com.eduvibe;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Punto de entrada de la API de Eduvibe.
 *
 * El esquema de la base de datos lo crean y versionan las migraciones de Flyway
 * (src/main/resources/db/migration). Hibernate trabaja en modo validate: si una
 * entidad no encaja con el esquema, la aplicación no arranca.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
@EnableAsync
public class EduvibeApplication {

    public static void main(String[] args) {
        SpringApplication.run(EduvibeApplication.class, args);
    }
}
