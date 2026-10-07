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

    private JwtService servicio;
    private User usuario;

    @BeforeEach
    void preparar() {
        AppProperties propiedades = new AppProperties(null,
                new AppProperties.Jwt("un-secreto-de-pruebas-de-al-menos-32-bytes!!", 8),
                null, null, null, null);
        servicio = new JwtService(propiedades, new MockEnvironment());

        Organization centro = new Organization("Centro", null);
        centro.setId(UUID.randomUUID());
        usuario = new User(centro, "ana@centro.es", "Ana", UserRole.STUDENT);
        usuario.setId(UUID.randomUUID());
    }

    @Test
    @DisplayName("el token lleva un id propio y la versión de sesiones del usuario")
    void lleva_id_y_version() {
        usuario.invalidarSesiones();
        usuario.invalidarSesiones();

        AuthenticatedUser identidad = servicio.leer(servicio.emitirPara(usuario));

        assertThat(identidad.id()).isEqualTo(usuario.getId());
        assertThat(identidad.sesion().id()).isNotNull();
        assertThat(identidad.sesion().version()).isEqualTo(2);
        assertThat(identidad.sesion().expiresAt()).isAfter(java.time.Instant.now());
    }

    @Test
    @DisplayName("dos tokens del mismo usuario tienen ids distintos, para poder revocar uno sin el otro")
    void idsDistintos() {
        AuthenticatedUser primero = servicio.leer(servicio.emitirPara(usuario));
        AuthenticatedUser segundo = servicio.leer(servicio.emitirPara(usuario));

        assertThat(primero.sesion().id()).isNotEqualTo(segundo.sesion().id());
    }

    @Test
    @DisplayName("con el perfil prod no arranca si falta JWT_SECRET")
    void prodExigeSecreto() {
        AppProperties sinSecreto = new AppProperties(null, new AppProperties.Jwt("", 8), null, null, null, null);
        MockEnvironment prod = new MockEnvironment();
        prod.setActiveProfiles("prod");

        assertThatThrownBy(() -> new JwtService(sinSecreto, prod))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
    }

    @Test
    @DisplayName("sin perfil prod, un secreto vacío genera una clave aleatoria y arranca")
    void desarrolloAceptaSinSecreto() {
        AppProperties sinSecreto = new AppProperties(null, new AppProperties.Jwt("", 8), null, null, null, null);

        JwtService local = new JwtService(sinSecreto, new MockEnvironment());

        assertThat(local.leer(local.emitirPara(usuario))).isNotNull();
    }

    @Test
    @DisplayName("un token manipulado se rechaza")
    void tokenManipulado() {
        String token = servicio.emitirPara(usuario);

        assertThat(servicio.leer(token + "x")).isNull();
        assertThat(servicio.leer("no-es-un-token")).isNull();
    }
}
