package com.eduvibe.service;

import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.eduvibe.config.AppProperties;
import com.eduvibe.model.LoginAttempt;
import com.eduvibe.repository.LoginAttemptRepository;

import lombok.RequiredArgsConstructor;

/**
 * Límite de intentos de inicio de sesión: tras varios fallos seguidos de un
 * mismo email se rechaza cualquier intento durante un tiempo.
 *
 * Los métodos que escriben van en una transacción propia (REQUIRES_NEW) por
 * una razón concreta: el login termina lanzando una excepción cuando falla, y
 * eso deshace la transacción en curso. Sin una transacción aparte, el fallo
 * que acabamos de apuntar se perdería justo en el caso en que hay que guardarlo.
 */
@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    private static final Logger LOG = LoggerFactory.getLogger(LoginAttemptService.class);

    /** Cuánto se conserva el historial de intentos. */
    private static final Duration RETENTION = Duration.ofDays(90);

    private final LoginAttemptRepository repository;
    private final AppProperties properties;

    /** Minutos que dura el bloqueo, para poder avisar al usuario. */
    public long minutesOfLockout() {
        return properties.login().lockoutMinutes();
    }

    @Transactional(readOnly = true)
    public boolean isLocked(String email) {
        Instant from = Instant.now().minus(Duration.ofMinutes(properties.login().lockoutMinutes()));
        long failures = repository.countByEmailAndSucceededFalseAndAttemptedAtAfter(email, from);
        return failures >= properties.login().maxFailedAttempts();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(String email, String ip) {
        repository.save(new LoginAttempt(email, ip, false));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuccess(String email, String ip) {
        repository.deleteFailuresByEmail(email);
        repository.save(new LoginAttempt(email, ip, true));
    }

    /** Cada noche se purga lo que supera el plazo de retención. */
    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void purgeOld() {
        int deleted = repository.deleteOlderThan(Instant.now().minus(RETENTION));
        LOG.info("Purgados {} intentos de login de más de {} días", deleted, RETENTION.toDays());
    }
}
