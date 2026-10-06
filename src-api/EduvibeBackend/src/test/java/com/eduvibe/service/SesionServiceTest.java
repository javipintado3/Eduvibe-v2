package com.eduvibe.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.eduvibe.model.Organization;
import com.eduvibe.model.RevokedToken;
import com.eduvibe.model.User;
import com.eduvibe.model.enums.UserRole;
import com.eduvibe.model.enums.UserStatus;
import com.eduvibe.repository.RevokedTokenRepository;
import com.eduvibe.repository.UserRepository;
import com.eduvibe.security.AuthenticatedUser;

@ExtendWith(MockitoExtension.class)
class SesionServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RevokedTokenRepository revokedTokenRepository;

    private SesionService servicio;

    private final UUID idUsuario = UUID.randomUUID();
    private final UUID idToken = UUID.randomUUID();
    private User usuario;

    @BeforeEach
    void preparar() {
        servicio = new SesionService(userRepository, revokedTokenRepository);
        usuario = new User(new Organization("Centro", null), "ana@centro.es", "Ana", UserRole.STUDENT);
        usuario.activarCon("$2a$10$hash");
    }

    private AuthenticatedUser identidad(int version) {
        return new AuthenticatedUser(idUsuario, "ana@centro.es", "Ana", UserRole.STUDENT, UUID.randomUUID(),
                new AuthenticatedUser.Sesion(idToken, version, Instant.now().plusSeconds(3600)));
    }

    @Nested
    @DisplayName("¿Sigue vigente el token?")
    class EstaVigente {

        @Test
        @DisplayName("sí, si no está revocado, la versión coincide y la cuenta está activa")
        void vigente() {
            when(userRepository.findById(idUsuario)).thenReturn(Optional.of(usuario));

            assertThat(servicio.estaVigente(identidad(0))).isTrue();
        }

        @Test
        @DisplayName("no, si el token se ha revocado al cerrar sesión")
        void revocado() {
            when(userRepository.findById(idUsuario)).thenReturn(Optional.of(usuario));
            when(revokedTokenRepository.existsById(idToken)).thenReturn(true);

            assertThat(servicio.estaVigente(identidad(0))).isFalse();
        }

        @Test
        @DisplayName("no, si desde que se emitió se han invalidado todas las sesiones del usuario")
        void versionAntigua() {
            usuario.invalidarSesiones();
            when(userRepository.findById(idUsuario)).thenReturn(Optional.of(usuario));

            assertThat(servicio.estaVigente(identidad(0))).isFalse();
        }

        @Test
        @DisplayName("no, si la cuenta se ha desactivado: el acceso se corta al instante, no a las 8 horas")
        void cuentaDesactivada() {
            usuario.setStatus(UserStatus.DISABLED);
            when(userRepository.findById(idUsuario)).thenReturn(Optional.of(usuario));

            assertThat(servicio.estaVigente(identidad(0))).isFalse();
        }

        @Test
        @DisplayName("no, si el usuario ya no existe")
        void usuarioInexistente() {
            when(userRepository.findById(idUsuario)).thenReturn(Optional.empty());

            assertThat(servicio.estaVigente(identidad(0))).isFalse();
        }

        @Test
        @DisplayName("un token anterior a la revocación (sin id) sigue valiendo mientras no cambie la versión")
        void tokenSinId() {
            when(userRepository.findById(idUsuario)).thenReturn(Optional.of(usuario));
            AuthenticatedUser sinId = new AuthenticatedUser(idUsuario, "ana@centro.es", "Ana", UserRole.STUDENT,
                    UUID.randomUUID(), new AuthenticatedUser.Sesion(null, 0, Instant.now().plusSeconds(60)));

            assertThat(servicio.estaVigente(sinId)).isTrue();
            verify(revokedTokenRepository, never()).existsById(any());
        }
    }

    @Nested
    @DisplayName("Cerrar sesión")
    class Cerrar {

        @Test
        @DisplayName("apunta el token concreto como revocado, con su caducidad para poder purgarlo")
        void apuntaElToken() {
            AuthenticatedUser identidad = identidad(0);

            servicio.cerrar(identidad);

            ArgumentCaptor<RevokedToken> guardado = ArgumentCaptor.forClass(RevokedToken.class);
            verify(revokedTokenRepository).save(guardado.capture());
            assertThat(guardado.getValue().getJti()).isEqualTo(idToken);
            assertThat(guardado.getValue().getUserId()).isEqualTo(idUsuario);
            assertThat(guardado.getValue().getExpiresAt()).isEqualTo(identidad.sesion().expiresAt());
        }

        @Test
        @DisplayName("cerrar dos veces la misma sesión no falla ni duplica")
        void idempotente() {
            when(revokedTokenRepository.existsById(idToken)).thenReturn(true);

            servicio.cerrar(identidad(0));

            verify(revokedTokenRepository, never()).save(any());
        }

        @Test
        @DisplayName("la purga borra los revocados que ya habrían caducado")
        void purga() {
            when(revokedTokenRepository.borrarCaducadosAntesDe(any(Instant.class))).thenReturn(2);

            servicio.purgarCaducados();

            verify(revokedTokenRepository).borrarCaducadosAntesDe(any(Instant.class));
        }
    }
}
