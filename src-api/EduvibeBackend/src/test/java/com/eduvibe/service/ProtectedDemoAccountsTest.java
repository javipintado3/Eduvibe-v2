package com.eduvibe.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.eduvibe.exception.ProtectedDemoAccountException;
import com.eduvibe.model.Organization;
import com.eduvibe.model.User;
import com.eduvibe.model.enums.UserRole;

class ProtectedDemoAccountsTest {

    private User account(String email) {
        return new User(new Organization("Centro", null), email, "Ana", UserRole.STUDENT);
    }

    @Test
    @DisplayName("protege las cuentas de la lista y deja pasar al resto")
    void protegeSoloLasDeLaLista() {
        ProtectedDemoAccounts guard = new ProtectedDemoAccounts("admin@demo.es, ana@demo.es");

        assertThat(guard.isProtected(account("admin@demo.es"))).isTrue();
        assertThat(guard.isProtected(account("ana@demo.es"))).isTrue();
        assertThat(guard.isProtected(account("otra@demo.es"))).isFalse();
    }

    @Test
    @DisplayName("no distingue mayúsculas ni espacios al leer la lista")
    void normalizaLaLista() {
        ProtectedDemoAccounts guard = new ProtectedDemoAccounts("  Admin@Demo.ES ,, ");

        assertThat(guard.isProtected(account("admin@demo.es"))).isTrue();
    }

    @Test
    @DisplayName("con la lista vacía no protege a nadie, que es lo normal fuera de la demo")
    void vaciaNoProtege() {
        ProtectedDemoAccounts guard = new ProtectedDemoAccounts("");

        assertThat(guard.isProtected(account("admin@demo.es"))).isFalse();
        assertThatCode(() -> guard.requireNotProtected(account("admin@demo.es"))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("exigir lanza un 403 con un mensaje claro si la cuenta es compartida")
    void exigirLanza() {
        ProtectedDemoAccounts guard = new ProtectedDemoAccounts("admin@demo.es");

        assertThatThrownBy(() -> guard.requireNotProtected(account("admin@demo.es")))
                .isInstanceOf(ProtectedDemoAccountException.class)
                .hasMessageContaining("cuenta de demostración compartida");
    }
}
