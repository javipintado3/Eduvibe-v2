package com.eduvibe.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import com.eduvibe.config.AppProperties;
import com.eduvibe.model.Organization;
import com.eduvibe.model.User;
import com.eduvibe.model.enums.UserRole;

class JwtServiceTest {

    private JwtService service;
    private User user;

    @BeforeEach
    void preparar() {
        AppProperties properties = new AppProperties(null,
                new AppProperties.Jwt("un-secreto-de-pruebas-de-al-menos-32-bytes!!", 8, 0),
                null, null, null, null, null);
        service = new JwtService(properties, new MockEnvironment());

        Organization organization = new Organization("Centro", null);
        organization.setId(UUID.randomUUID());
        user = new User(organization, "ana@centro.es", "Ana", UserRole.STUDENT);
        user.setId(UUID.randomUUID());
    }

    @Test
    @DisplayName("el token lleva un id propio y la versión de sesiones del usuario")
    void lleva_id_y_version() {
        user.invalidateSessions();
        user.invalidateSessions();

        AuthenticatedUser identity = service.read(service.issueFor(user));

        assertThat(identity.id()).isEqualTo(user.getId());
        assertThat(identity.session().id()).isNotNull();
        assertThat(identity.session().version()).isEqualTo(2);
        assertThat(identity.session().expiresAt()).isAfter(java.time.Instant.now());
    }

    @Test
    @DisplayName("si se configuran minutos, mandan sobre las horas")
    void validezEnMinutos() {
        JwtService withMinutes = new JwtService(new AppProperties(null,
                new AppProperties.Jwt("un-secreto-de-pruebas-de-al-menos-32-bytes!!", 8, 30),
                null, null, null, null, null), new MockEnvironment());

        java.time.Instant expiry = withMinutes.read(withMinutes.issueFor(user)).session().expiresAt();

        assertThat(expiry).isBefore(java.time.Instant.now().plus(java.time.Duration.ofMinutes(31)));
        assertThat(expiry).isAfter(java.time.Instant.now().plus(java.time.Duration.ofMinutes(29)));
    }

    @Test
    @DisplayName("dos tokens del mismo usuario tienen ids distintos, para poder revocar uno sin el otro")
    void idsDistintos() {
        AuthenticatedUser first = service.read(service.issueFor(user));
        AuthenticatedUser second = service.read(service.issueFor(user));

        assertThat(first.session().id()).isNotEqualTo(second.session().id());
    }

    @Test
    @DisplayName("con el perfil prod no arranca si falta JWT_SECRET")
    void prodExigeSecreto() {
        AppProperties withoutSecret = new AppProperties(null, new AppProperties.Jwt("", 8, 0), null, null, null, null, null);
        MockEnvironment prod = new MockEnvironment();
        prod.setActiveProfiles("prod");

        assertThatThrownBy(() -> new JwtService(withoutSecret, prod))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
    }

    @Test
    @DisplayName("sin perfil prod, un secreto vacío genera una clave aleatoria y arranca")
    void desarrolloAceptaSinSecreto() {
        AppProperties withoutSecret = new AppProperties(null, new AppProperties.Jwt("", 8, 0), null, null, null, null, null);

        JwtService local = new JwtService(withoutSecret, new MockEnvironment());

        assertThat(local.read(local.issueFor(user))).isNotNull();
    }

    @Test
    @DisplayName("un token manipulado se rechaza")
    void tokenManipulado() {
        String token = service.issueFor(user);

        assertThat(service.read(token + "x")).isNull();
        assertThat(service.read("no-es-un-token")).isNull();
    }
}
