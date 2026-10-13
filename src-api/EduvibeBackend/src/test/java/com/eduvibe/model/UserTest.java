package com.eduvibe.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.eduvibe.model.enums.UserRole;
import com.eduvibe.model.enums.UserStatus;

class UserTest {

    private User nuevoUsuario() {
        return new User(new Organization("Centro", null), "Ana@Centro.es", "Ana", UserRole.STUDENT);
    }

    @Nested
    @DisplayName("Normalización del email")
    class Normalizacion {

        @Test
        @DisplayName("pasa a minúsculas y quita los espacios sobrantes")
        void normaliza() {
            assertThat(User.normalizarEmail("  Ana@Centro.ES  ")).isEqualTo("ana@centro.es");
        }

        @Test
        @DisplayName("deja pasar el null sin romperse")
        void toleraNull() {
            assertThat(User.normalizarEmail(null)).isNull();
        }

        @Test
        @DisplayName("se aplica también al construir y al asignar")
        void seAplicaAlAsignar() {
            User usuario = nuevoUsuario();
            assertThat(usuario.getEmail()).isEqualTo("ana@centro.es");

            usuario.setEmail("OTRA@Centro.es");
            assertThat(usuario.getEmail()).isEqualTo("otra@centro.es");
        }
    }

    @Nested
    @DisplayName("Ciclo de vida de la cuenta")
    class CicloDeVida {

        @Test
        @DisplayName("nace pendiente y sin contraseña")
        void naceP() {
            User usuario = nuevoUsuario();

            assertThat(usuario.getStatus()).isEqualTo(UserStatus.PENDING);
            assertThat(usuario.getPasswordHash()).isNull();
        }

        @Test
        @DisplayName("una cuenta pendiente no puede iniciar sesión")
        void pendienteNoEntra() {
            assertThat(nuevoUsuario().puedeIniciarSesion()).isFalse();
        }

        @Test
        @DisplayName("al activarse queda con contraseña y en estado activo")
        void seActiva() {
            User usuario = nuevoUsuario();
            usuario.activarCon("$2a$10$hash");

            assertThat(usuario.getStatus()).isEqualTo(UserStatus.ACTIVE);
            assertThat(usuario.puedeIniciarSesion()).isTrue();
        }

        @Test
        @DisplayName("una cuenta desactivada no puede iniciar sesión aunque tenga contraseña")
        void desactivadaNoEntra() {
            User usuario = nuevoUsuario();
            usuario.activarCon("$2a$10$hash");
            usuario.setStatus(UserStatus.DISABLED);

            assertThat(usuario.puedeIniciarSesion()).isFalse();
        }
    }

    @Nested
    @DisplayName("Eliminación por la administración")
    class Eliminacion {

        @Test
        @DisplayName("anonimiza el nombre, inutiliza el acceso y deja constancia de cuándo")
        void anonimiza() {
            User usuario = nuevoUsuario();
            usuario.activarCon("$2a$10$hash");

            usuario.eliminar();

            assertThat(usuario.getName()).isEqualTo(User.NOMBRE_ELIMINADO);
            assertThat(usuario.getEmail()).endsWith("@eduvibe.invalid");
            assertThat(usuario.puedeIniciarSesion()).isFalse();
            assertThat(usuario.getStatus()).isEqualTo(UserStatus.DISABLED);
            assertThat(usuario.getDeletedAt()).isNotNull();
        }

        @Test
        @DisplayName("una cuenta normal no figura como eliminada")
        void porDefectoNoEstaEliminada() {
            assertThat(nuevoUsuario().estaEliminado()).isFalse();
        }
    }
}
