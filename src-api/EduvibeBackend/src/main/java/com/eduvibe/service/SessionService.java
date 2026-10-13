package com.eduvibe.service;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.eduvibe.model.RevokedToken;
import com.eduvibe.model.User;
import com.eduvibe.model.enums.UserStatus;
import com.eduvibe.repository.RevokedTokenRepository;
import com.eduvibe.repository.UserRepository;
import com.eduvibe.security.AuthenticatedUser;

import lombok.RequiredArgsConstructor;

/**
 * Decide si un token que es válido por firma y plazo sigue valiendo, y permite
 * revocarlo.
 *
 * Un JWT por sí solo no se puede anular: vale hasta que caduca. Para que cerrar
 * sesión o cambiar la contraseña tengan efecto real, en cada petición se
 * comprueba además contra la base de datos que el token no se ha revocado, que
 * la versión de sesiones sigue siendo la suya y que la cuenta sigue activa.
 * Esto último hace que desactivar a un usuario le corte el acceso al instante y
 * no a las 8 horas.
 */
@Service
@RequiredArgsConstructor
public class SessionService {

    private static final Logger LOG = LoggerFactory.getLogger(SessionService.class);

    private final UserRepository userRepository;
    private final RevokedTokenRepository revokedTokenRepository;

    @Transactional(readOnly = true)
    public boolean isValid(AuthenticatedUser identity) {
        User user = userRepository.findById(identity.id()).orElse(null);

        if (user == null || user.getStatus() != UserStatus.ACTIVE) {
            return false;
        }

        AuthenticatedUser.Session session = identity.session();
        if (session == null) {
            return true;
        }

        boolean revoked = session.id() != null && revokedTokenRepository.existsById(session.id());
        return !revoked && session.version() == user.getTokenVersion();
    }

    /** Cierra la sesión del token con el que se hace la petición, sin tocar las de otros dispositivos. */
    @Transactional
    public void close(AuthenticatedUser identity) {
        AuthenticatedUser.Session session = identity.session();

        // Un token anterior a la revocación no tiene id y no se puede apuntar;
        // caducará solo. Es el único caso, y desaparece en cuanto caducan.
        if (session == null || session.id() == null || revokedTokenRepository.existsById(session.id())) {
            return;
        }
        revokedTokenRepository.save(new RevokedToken(session.id(), identity.id(), session.expiresAt()));
    }

    /** Cada noche se purgan los tokens revocados que ya habrían caducado. */
    @Scheduled(cron = "0 45 3 * * *")
    @Transactional
    public void purgeExpired() {
        int deleted = revokedTokenRepository.deleteExpiredBefore(Instant.now());
        LOG.info("Purgados {} tokens revocados ya caducados", deleted);
    }
}
