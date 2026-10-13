package com.eduvibe.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientIpTest {

    private MockHttpServletRequest request(String resent) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("172.22.0.5");
        if (resent != null) {
            request.addHeader("X-Forwarded-For", resent);
        }
        return request;
    }

    @Test
    @DisplayName("sin proxy de confianza ignora X-Forwarded-For, porque cualquiera puede falsearla")
    void sinProxyDeConfianza() {
        assertThat(ClientIp.of(request("1.2.3.4"), false)).isEqualTo("172.22.0.5");
    }

    @Test
    @DisplayName("con proxy de confianza usa la dirección que añadió el proxy, la última")
    void conProxyDeConfianza() {
        assertThat(ClientIp.of(request("9.9.9.9, 203.0.113.7"), true)).isEqualTo("203.0.113.7");
    }

    @Test
    @DisplayName("con proxy de confianza pero sin cabecera usa la dirección de la conexión")
    void sinCabecera() {
        assertThat(ClientIp.of(request(null), true)).isEqualTo("172.22.0.5");
    }
}
