package com.eduvibe.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
        servicio = new JwtService(propiedades);

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
    @DisplayName("un token manipulado se rechaza")
    void tokenManipulado() {
        String token = servicio.emitirPara(usuario);

        assertThat(servicio.leer(token + "x")).isNull();
        assertThat(servicio.leer("no-es-un-token")).isNull();
    }
}
