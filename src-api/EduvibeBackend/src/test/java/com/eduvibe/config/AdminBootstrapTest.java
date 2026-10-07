package com.eduvibe.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.eduvibe.model.Organization;
import com.eduvibe.model.User;
import com.eduvibe.model.enums.UserRole;
import com.eduvibe.model.enums.UserStatus;
import com.eduvibe.repository.OrganizationRepository;
import com.eduvibe.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class AdminBootstrapTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private OrganizationRepository organizationRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    private AdminBootstrap bootstrap;

    @BeforeEach
    void preparar() {
        bootstrap = new AdminBootstrap(userRepository, organizationRepository, passwordEncoder);
        ReflectionTestUtils.setField(bootstrap, "email", "Admin@Centro.es");
        ReflectionTestUtils.setField(bootstrap, "password", "una-contraseña-larga");
        ReflectionTestUtils.setField(bootstrap, "nombre", "Administrador");
        ReflectionTestUtils.setField(bootstrap, "organizacion", "Mi centro");
    }

    @Test
    @DisplayName("con la base vacía crea un administrador activo con la contraseña cifrada")
    void creaElPrimerAdmin() {
        when(userRepository.count()).thenReturn(0L);
        when(organizationRepository.save(any(Organization.class))).thenAnswer(i -> i.getArgument(0));
        when(passwordEncoder.encode("una-contraseña-larga")).thenReturn("hash");

        bootstrap.run(null);

        ArgumentCaptor<User> guardado = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(guardado.capture());
        assertThat(guardado.getValue().getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(guardado.getValue().getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(guardado.getValue().getEmail()).isEqualTo("admin@centro.es");
        assertThat(guardado.getValue().getPasswordHash()).isEqualTo("hash");
    }

    @Test
    @DisplayName("si ya hay usuarios no hace nada")
    void noTocaUnaBaseEnUso() {
        when(userRepository.count()).thenReturn(3L);

        bootstrap.run(null);

        verify(userRepository, never()).save(any());
        verify(organizationRepository, never()).save(any());
    }

    @Test
    @DisplayName("sin credenciales configuradas no crea nada ni falla")
    void sinCredenciales() {
        ReflectionTestUtils.setField(bootstrap, "password", "");
        when(userRepository.count()).thenReturn(0L);

        bootstrap.run(null);

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("rechaza una contraseña demasiado corta")
    void passwordCorta() {
        ReflectionTestUtils.setField(bootstrap, "password", "corta");
        when(userRepository.count()).thenReturn(0L);

        assertThatThrownBy(() -> bootstrap.run(null)).isInstanceOf(IllegalStateException.class);
        verify(userRepository, never()).save(any());
    }
}
