package com.eduvibe.model.enums;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EnumWithValueTest {

    @Test
    @DisplayName("convierte el valor almacenado en la constante")
    void convierte() {
        assertThat(UserRole.fromValue("teacher")).isEqualTo(UserRole.TEACHER);
        assertThat(UserStatus.fromValue("pending")).isEqualTo(UserStatus.PENDING);
        assertThat(EnrollmentRole.fromValue("student")).isEqualTo(EnrollmentRole.STUDENT);
        assertThat(SubmissionStatus.fromValue("graded")).isEqualTo(SubmissionStatus.GRADED);
    }

    @Test
    @DisplayName("no distingue mayúsculas")
    void sinDistinguirMayusculas() {
        assertThat(UserRole.fromValue("ADMIN")).isEqualTo(UserRole.ADMIN);
        assertThat(UserRole.fromValue("Teacher")).isEqualTo(UserRole.TEACHER);
    }

    @Test
    @DisplayName("un valor desconocido falla diciendo cuáles se admiten")
    void valorDesconocido() {
        assertThatThrownBy(() -> UserRole.fromValue("jefe"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("jefe")
                .hasMessageContaining("admin, teacher, student, guardian");
    }

    @Test
    @DisplayName("el valor almacenado va en minúsculas aunque la constante sea mayúscula")
    void elValorVaEnMinusculas() {
        // Es lo que esperan las restricciones CHECK de las columnas
        assertThat(UserRole.ADMIN.getValue()).isEqualTo("admin");
        assertThat(SubmissionStatus.SUBMITTED.getValue()).isEqualTo("submitted");
    }
}
