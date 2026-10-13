package com.eduvibe.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.eduvibe.model.enums.UserRole;
import com.eduvibe.model.enums.UserStatus;

class UserTest {

    private User newUser() {
        return new User(new Organization("Centro", null), "Ana@Centro.es", "Ana", UserRole.STUDENT);
    }

    @Nested
    @DisplayName("Normalización del email")
    class Normalization {

        @Test
        @DisplayName("pasa a minúsculas y quita los espacios sobrantes")
        void normaliza() {
            assertThat(User.normalizeEmail("  Ana@Centro.ES  ")).isEqualTo("ana@centro.es");
        }

        @Test
        @DisplayName("deja pasar el null sin romperse")
        void toleraNull() {
            assertThat(User.normalizeEmail(null)).isNull();
        }

        @Test
        @DisplayName("se aplica también al construir y al asignar")
        void seAplicaAlAsignar() {
            User user = newUser();
            assertThat(user.getEmail()).isEqualTo("ana@centro.es");

            user.setEmail("OTRA@Centro.es");
            assertThat(user.getEmail()).isEqualTo("otra@centro.es");
        }
    }

    @Nested
    @DisplayName("Ciclo de vida de la cuenta")
    class Lifecycle {

        @Test
        @DisplayName("nace pendiente y sin contraseña")
        void naceP() {
            User user = newUser();

            assertThat(user.getStatus()).isEqualTo(UserStatus.PENDING);
            assertThat(user.getPasswordHash()).isNull();
        }

        @Test
        @DisplayName("una cuenta pendiente no puede iniciar sesión")
        void pendienteNoEntra() {
            assertThat(newUser().canStartSession()).isFalse();
        }

        @Test
        @DisplayName("al activarse queda con contraseña y en estado activo")
        void seActiva() {
            User user = newUser();
            user.enableWith("$2a$10$hash");

            assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
            assertThat(user.canStartSession()).isTrue();
        }

        @Test
        @DisplayName("una cuenta desactivada no puede iniciar sesión aunque tenga contraseña")
        void desactivadaNoEntra() {
            User user = newUser();
            user.enableWith("$2a$10$hash");
            user.setStatus(UserStatus.DISABLED);

            assertThat(user.canStartSession()).isFalse();
        }
    }

    @Nested
    @DisplayName("Eliminación por la administración")
    class Deletion {

        @Test
        @DisplayName("anonimiza el nombre, inutiliza el acceso y deja constancia de cuándo")
        void anonimiza() {
            User user = newUser();
            user.enableWith("$2a$10$hash");

            user.delete();

            assertThat(user.getName()).isEqualTo(User.DELETED_NAME);
            assertThat(user.getEmail()).endsWith("@eduvibe.invalid");
            assertThat(user.canStartSession()).isFalse();
            assertThat(user.getStatus()).isEqualTo(UserStatus.DISABLED);
            assertThat(user.getDeletedAt()).isNotNull();
        }

        @Test
        @DisplayName("una cuenta normal no figura como eliminada")
        void porDefectoNoEstaEliminada() {
            assertThat(newUser().isDeleted()).isFalse();
        }
    }
}
