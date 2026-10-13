package com.eduvibe.repository.spec;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * Solo el contrato de "sin classId, sin filtro" es comprobable sin una base de
 * datos real: el predicado en sí (subconsulta contra enrollments) lo evalúa el
 * proveedor JPA, y este proyecto no tiene infraestructura de tests de
 * integración (Testcontainers) todavía. Ver {@link UserSpecifications#notEnrolledIn}.
 */
class UserSpecificationsTest {

    @Test
    void sinClassIdNoAplicaFiltro() {
        assertThat(UserSpecifications.notEnrolledIn(null)).isNull();
    }

    @Test
    void conClassIdDevuelveUnaSpecification() {
        assertThat(UserSpecifications.notEnrolledIn(UUID.randomUUID())).isNotNull();
    }
}
