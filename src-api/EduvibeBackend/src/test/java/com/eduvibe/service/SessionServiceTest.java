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
class SessionServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RevokedTokenRepository revokedTokenRepository;

    private SessionService service;

    private final UUID userId = UUID.randomUUID();
    private final UUID idToken = UUID.randomUUID();
    private User user;

    @BeforeEach
    void preparar() {
        service = new SessionService(userRepository, revokedTokenRepository);
        user = new User(new Organization("Centro", null), "ana@centro.es", "Ana", UserRole.STUDENT);
        user.enableWith("$2a$10$hash");
    }

    private AuthenticatedUser identity(int version) {
        return new AuthenticatedUser(userId, "ana@centro.es", "Ana", UserRole.STUDENT, UUID.randomUUID(),
                new AuthenticatedUser.Session(idToken, version, Instant.now().plusSeconds(3600)));
    }

    @Nested
    @DisplayName("¿Sigue vigente el token?")
    class IsValid {

        @Test
        @DisplayName("sí, si no está revocado, la versión coincide y la cuenta está activa")
        void vigente() {
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));

            assertThat(service.isValid(identity(0))).isTrue();
        }

        @Test
        @DisplayName("no, si el token se ha revocado al cerrar sesión")
        void revocado() {
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(revokedTokenRepository.existsById(idToken)).thenReturn(true);

            assertThat(service.isValid(identity(0))).isFalse();
        }

        @Test
        @DisplayName("no, si desde que se emitió se han invalidado todas las sesiones del usuario")
        void versionAntigua() {
            user.invalidateSessions();
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));

            assertThat(service.isValid(identity(0))).isFalse();
        }

        @Test
        @DisplayName("no, si la cuenta se ha desactivado: el acceso se corta al instante, no a las 8 horas")
        void cuentaDesactivada() {
            user.setStatus(UserStatus.DISABLED);
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));

            assertThat(service.isValid(identity(0))).isFalse();
        }

        @Test
        @DisplayName("no, si el usuario ya no existe")
        void usuarioInexistente() {
            when(userRepository.findById(userId)).thenReturn(Optional.empty());

            assertThat(service.isValid(identity(0))).isFalse();
        }

        @Test
        @DisplayName("un token anterior a la revocación (sin id) sigue valiendo mientras no cambie la versión")
        void tokenSinId() {
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            AuthenticatedUser withoutId = new AuthenticatedUser(userId, "ana@centro.es", "Ana", UserRole.STUDENT,
                    UUID.randomUUID(), new AuthenticatedUser.Session(null, 0, Instant.now().plusSeconds(60)));

            assertThat(service.isValid(withoutId)).isTrue();
            verify(revokedTokenRepository, never()).existsById(any());
        }
    }

    @Nested
    @DisplayName("Cerrar sesión")
    class Close {

        @Test
        @DisplayName("apunta el token concreto como revocado, con su caducidad para poder purgarlo")
        void apuntaElToken() {
            AuthenticatedUser identity = identity(0);

            service.close(identity);

            ArgumentCaptor<RevokedToken> saved = ArgumentCaptor.forClass(RevokedToken.class);
            verify(revokedTokenRepository).save(saved.capture());
            assertThat(saved.getValue().getJti()).isEqualTo(idToken);
            assertThat(saved.getValue().getUserId()).isEqualTo(userId);
            assertThat(saved.getValue().getExpiresAt()).isEqualTo(identity.session().expiresAt());
        }

        @Test
        @DisplayName("cerrar dos veces la misma sesión no falla ni duplica")
        void idempotente() {
            when(revokedTokenRepository.existsById(idToken)).thenReturn(true);

            service.close(identity(0));

            verify(revokedTokenRepository, never()).save(any());
        }

        @Test
        @DisplayName("la purga borra los revocados que ya habrían caducado")
        void purga() {
            when(revokedTokenRepository.deleteExpiredBefore(any(Instant.class))).thenReturn(2);

            service.purgeExpired();

            verify(revokedTokenRepository).deleteExpiredBefore(any(Instant.class));
        }
    }
}
