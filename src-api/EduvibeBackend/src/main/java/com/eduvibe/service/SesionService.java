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
public class SesionService {

    private static final Logger LOG = LoggerFactory.getLogger(SesionService.class);

    private final UserRepository userRepository;
    private final RevokedTokenRepository revokedTokenRepository;

    @Transactional(readOnly = true)
    public boolean estaVigente(AuthenticatedUser identidad) {
        User usuario = userRepository.findById(identidad.id()).orElse(null);

        if (usuario == null || usuario.getStatus() != UserStatus.ACTIVE) {
            return false;
        }

        AuthenticatedUser.Sesion sesion = identidad.sesion();
        if (sesion == null) {
            return true;
        }

        boolean revocado = sesion.id() != null && revokedTokenRepository.existsById(sesion.id());
        return !revocado && sesion.version() == usuario.getTokenVersion();
    }

    /** Cierra la sesión del token con el que se hace la petición, sin tocar las de otros dispositivos. */
    @Transactional
    public void cerrar(AuthenticatedUser identidad) {
        AuthenticatedUser.Sesion sesion = identidad.sesion();

        // Un token anterior a la revocación no tiene id y no se puede apuntar;
        // caducará solo. Es el único caso, y desaparece en cuanto caducan.
        if (sesion == null || sesion.id() == null || revokedTokenRepository.existsById(sesion.id())) {
            return;
        }
        revokedTokenRepository.save(new RevokedToken(sesion.id(), identidad.id(), sesion.expiresAt()));
    }

    /** Cada noche se purgan los tokens revocados que ya habrían caducado. */
    @Scheduled(cron = "0 45 3 * * *")
    @Transactional
    public void purgarCaducados() {
        int borrados = revokedTokenRepository.borrarCaducadosAntesDe(Instant.now());
        LOG.info("Purgados {} tokens revocados ya caducados", borrados);
    }
}
