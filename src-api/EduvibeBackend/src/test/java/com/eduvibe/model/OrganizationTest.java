package com.eduvibe.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OrganizationTest {

    @Test
    @DisplayName("sin dominio configurado se admite cualquier email")
    void sinDominioTodoVale() {
        Organization organization = new Organization("Centro", null);

        assertThat(organization.acceptsEmail("quien.sea@gmail.com")).isTrue();
    }

    @Test
    @DisplayName("con dominio configurado solo se admiten sus direcciones")
    void conDominioSoloEseDominio() {
        Organization organization = new Organization("Centro", "iesalixar.edu");

        assertThat(organization.acceptsEmail("ana@iesalixar.edu")).isTrue();
        assertThat(organization.acceptsEmail("ana@gmail.com")).isFalse();
    }

    @Test
    @DisplayName("la comprobación del dominio no distingue mayúsculas")
    void dominioSinDistinguirMayusculas() {
        Organization organization = new Organization("Centro", "IesAlixar.edu");

        assertThat(organization.acceptsEmail("Ana@IESALIXAR.EDU")).isTrue();
    }

    @Test
    @DisplayName("un dominio que solo coincide al final no cuela")
    void noValeTerminarParecido() {
        Organization organization = new Organization("Centro", "alixar.edu");

        // "ana@falsoalixar.edu" termina en "alixar.edu", pero el dominio no es ese
        assertThat(organization.acceptsEmail("ana@falsoalixar.edu")).isFalse();
    }
}
