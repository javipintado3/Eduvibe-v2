package com.eduvibe.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.eduvibe.exception.CuentaDeDemoProtegidaException;
import com.eduvibe.model.Organization;
import com.eduvibe.model.User;
import com.eduvibe.model.enums.UserRole;

class DemoCuentasProtegidasTest {

    private User cuenta(String email) {
        return new User(new Organization("Centro", null), email, "Ana", UserRole.STUDENT);
    }

    @Test
    @DisplayName("protege las cuentas de la lista y deja pasar al resto")
    void protegeSoloLasDeLaLista() {
        DemoCuentasProtegidas guardia = new DemoCuentasProtegidas("admin@demo.es, ana@demo.es");

        assertThat(guardia.esProtegida(cuenta("admin@demo.es"))).isTrue();
        assertThat(guardia.esProtegida(cuenta("ana@demo.es"))).isTrue();
        assertThat(guardia.esProtegida(cuenta("otra@demo.es"))).isFalse();
    }

    @Test
    @DisplayName("no distingue mayúsculas ni espacios al leer la lista")
    void normalizaLaLista() {
        DemoCuentasProtegidas guardia = new DemoCuentasProtegidas("  Admin@Demo.ES ,, ");

        assertThat(guardia.esProtegida(cuenta("admin@demo.es"))).isTrue();
    }

    @Test
    @DisplayName("con la lista vacía no protege a nadie, que es lo normal fuera de la demo")
    void vaciaNoProtege() {
        DemoCuentasProtegidas guardia = new DemoCuentasProtegidas("");

        assertThat(guardia.esProtegida(cuenta("admin@demo.es"))).isFalse();
        assertThatCode(() -> guardia.exigirNoProtegida(cuenta("admin@demo.es"))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("exigir lanza un 403 con un mensaje claro si la cuenta es compartida")
    void exigirLanza() {
        DemoCuentasProtegidas guardia = new DemoCuentasProtegidas("admin@demo.es");

        assertThatThrownBy(() -> guardia.exigirNoProtegida(cuenta("admin@demo.es")))
                .isInstanceOf(CuentaDeDemoProtegidaException.class)
                .hasMessageContaining("cuenta de demostración compartida");
    }
}
