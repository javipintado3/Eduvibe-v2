package com.eduvibe.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.eduvibe.config.AppProperties;
import com.eduvibe.model.enums.UserRole;
import com.fasterxml.jackson.databind.ObjectMapper;

class DemoReadOnlyFilterTest {

    private static final String EMAIL_DEMO = "alumna@eduvibe.demo";
    private static final String EMAIL_REAL = "ana@centro.es";

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest(name = "{0} /api/classes/1/forum de una cuenta demo se rechaza")
    @ValueSource(strings = {"POST", "PUT", "PATCH", "DELETE"})
    @DisplayName("una cuenta de demostración no puede escribir")
    void bloquea_escrituras(String metodo) throws Exception {
        MockFilterChain cadena = new MockFilterChain();
        MockHttpServletResponse respuesta = ejecutar(filtro("eduvibe.demo"), metodo, "/api/classes/1/forum",
                EMAIL_DEMO, cadena);

        assertThat(respuesta.getStatus()).isEqualTo(403);
        assertThat(respuesta.getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
                .contains(DemoReadOnlyFilter.MENSAJE);
        assertThat(cadena.getRequest()).as("la petición no debe llegar al controlador").isNull();
    }

    @ParameterizedTest(name = "{0} de una cuenta demo pasa")
    @ValueSource(strings = {"GET", "HEAD", "OPTIONS"})
    @DisplayName("una cuenta de demostración sí puede consultar")
    void deja_consultar(String metodo) throws Exception {
        MockFilterChain cadena = new MockFilterChain();
        MockHttpServletResponse respuesta = ejecutar(filtro("eduvibe.demo"), metodo, "/api/classes",
                EMAIL_DEMO, cadena);

        assertThat(respuesta.getStatus()).isEqualTo(200);
        assertThat(cadena.getRequest()).isNotNull();
    }

    @ParameterizedTest(name = "POST {0} de una cuenta demo pasa")
    @ValueSource(strings = {"/api/auth/login", "/api/auth/logout"})
    @DisplayName("entrar y salir no cuentan como escribir")
    void deja_entrar_y_salir(String ruta) throws Exception {
        MockFilterChain cadena = new MockFilterChain();
        MockHttpServletResponse respuesta = ejecutar(filtro("eduvibe.demo"), "POST", ruta, EMAIL_DEMO, cadena);

        assertThat(respuesta.getStatus()).isEqualTo(200);
        assertThat(cadena.getRequest()).isNotNull();
    }

    @Test
    @DisplayName("bloquea también cambiar la contraseña, para que nadie pueda cerrar la demo a los demás")
    void bloquea_cambio_de_contrasena() throws Exception {
        MockHttpServletResponse respuesta = ejecutar(filtro("eduvibe.demo"), "PUT", "/api/auth/password",
                EMAIL_DEMO, new MockFilterChain());

        assertThat(respuesta.getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("un usuario real puede escribir aunque la demo esté activada")
    void usuario_real_escribe() throws Exception {
        MockFilterChain cadena = new MockFilterChain();
        MockHttpServletResponse respuesta = ejecutar(filtro("eduvibe.demo"), "POST", "/api/classes/1/forum",
                EMAIL_REAL, cadena);

        assertThat(respuesta.getStatus()).isEqualTo(200);
        assertThat(cadena.getRequest()).isNotNull();
    }

    @Test
    @DisplayName("el dominio se compara sin mirar mayúsculas y entero: otro dominio parecido no cuenta")
    void compara_el_dominio_entero() throws Exception {
        assertThat(ejecutar(filtro("eduvibe.demo"), "POST", "/api/x", "Ana@EDUVIBE.DEMO", new MockFilterChain())
                .getStatus()).isEqualTo(403);

        assertThat(ejecutar(filtro("eduvibe.demo"), "POST", "/api/x", "ana@noeduvibe.demo", new MockFilterChain())
                .getStatus()).isEqualTo(200);
        assertThat(ejecutar(filtro("eduvibe.demo"), "POST", "/api/x", "ana@eduvibe.demo.es", new MockFilterChain())
                .getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("sin sesión no hace nada: las rutas públicas siguen su camino")
    void sin_sesion_no_actua() throws Exception {
        MockFilterChain cadena = new MockFilterChain();
        MockHttpServletRequest peticion = new MockHttpServletRequest("POST", "/api/auth/register");
        MockHttpServletResponse respuesta = new MockHttpServletResponse();

        filtro("eduvibe.demo").doFilter(peticion, respuesta, cadena);

        assertThat(respuesta.getStatus()).isEqualTo(200);
        assertThat(cadena.getRequest()).isNotNull();
    }

    @Test
    @DisplayName("apagado (sin dominio configurado) no bloquea a nadie")
    void apagado_no_bloquea() throws Exception {
        assertThat(ejecutar(filtro(""), "POST", "/api/x", EMAIL_DEMO, new MockFilterChain()).getStatus())
                .isEqualTo(200);
        assertThat(ejecutar(filtro(null), "POST", "/api/x", EMAIL_DEMO, new MockFilterChain()).getStatus())
                .isEqualTo(200);

        DemoReadOnlyFilter sinAjuste = new DemoReadOnlyFilter(
                new AppProperties(null, null, null, null, null, null, null, null), objectMapper);
        assertThat(ejecutar(sinAjuste, "POST", "/api/x", EMAIL_DEMO, new MockFilterChain()).getStatus())
                .isEqualTo(200);
    }

    private DemoReadOnlyFilter filtro(String dominio) {
        AppProperties propiedades = new AppProperties(null, null, null, null, null, null, null,
                new AppProperties.Demo(dominio));
        return new DemoReadOnlyFilter(propiedades, objectMapper);
    }

    private MockHttpServletResponse ejecutar(DemoReadOnlyFilter filtro, String metodo, String ruta, String email,
                                             MockFilterChain cadena) throws Exception {
        AuthenticatedUser usuario = new AuthenticatedUser(UUID.randomUUID(), email, "Alguien",
                UserRole.STUDENT, UUID.randomUUID());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario, null, java.util.List.of()));

        MockHttpServletRequest peticion = new MockHttpServletRequest(metodo, ruta);
        MockHttpServletResponse respuesta = new MockHttpServletResponse();
        filtro.doFilter(peticion, respuesta, cadena);
        return respuesta;
    }
}
