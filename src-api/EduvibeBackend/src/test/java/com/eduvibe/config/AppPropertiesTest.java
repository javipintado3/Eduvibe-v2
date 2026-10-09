package com.eduvibe.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/**
 * Comprueba que Spring sabe leer {@link AppProperties} desde la configuración.
 *
 * El registro tiene un constructor secundario (para no tocar las pruebas que lo
 * construyen a mano) y con más de un constructor Spring podría no saber cuál usar:
 * se habría descubierto al arrancar la API, no antes.
 */
class AppPropertiesTest {

    @Configuration
    @EnableConfigurationProperties(AppProperties.class)
    static class Configuracion {
    }

    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
            .withUserConfiguration(Configuracion.class);

    @Test
    @DisplayName("lee el dominio de la demo y el resto de ajustes")
    void lee_el_dominio_de_la_demo() {
        contexto.withPropertyValues(
                        "app.demo.read-only-email-domain=eduvibe.demo",
                        "app.cors.allowed-origins=http://localhost",
                        "app.jwt.secret=un-secreto-de-pruebas-de-al-menos-32-bytes!!",
                        "app.jwt.expiration-hours=8")
                .run(c -> {
                    assertThat(c).hasNotFailed();
                    AppProperties propiedades = c.getBean(AppProperties.class);
                    assertThat(propiedades.demo().readOnlyEmailDomain()).isEqualTo("eduvibe.demo");
                    assertThat(propiedades.demo().esSoloLectura("ana@eduvibe.demo")).isTrue();
                    assertThat(propiedades.jwt().expirationHours()).isEqualTo(8);
                });
    }

    @Test
    @DisplayName("con el valor vacío (el de por defecto) la función queda apagada")
    void vacio_la_deja_apagada() {
        contexto.withPropertyValues("app.demo.read-only-email-domain=")
                .run(c -> {
                    assertThat(c).hasNotFailed();
                    assertThat(c.getBean(AppProperties.class).demo().esSoloLectura("ana@eduvibe.demo")).isFalse();
                });
    }
}
