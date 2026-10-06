package com.eduvibe.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.eduvibe.model.PasswordReset;

public interface PasswordResetRepository extends JpaRepository<PasswordReset, UUID> {

    @Modifying
    @Query("DELETE FROM PasswordReset p WHERE p.user.id = :userId")
    int borrarDe(@Param("userId") UUID userId);

    /** Se busca por el hash: el token en claro nunca llega a la base de datos. */
    Optional<PasswordReset> findByTokenHash(String tokenHash);

    /** ¿Ha pedido este usuario un enlace desde el instante indicado? */
    boolean existsByUserIdAndCreatedAtAfter(UUID userId, Instant desde);

    /**
     * Invalida los enlaces que siguen vivos de un usuario: al emitir uno nuevo,
     * o al cambiar la contraseña, para no dejar enlaces antiguos utilizables.
     */
    @Modifying
    @Query("UPDATE PasswordReset p SET p.usedAt = CURRENT_TIMESTAMP "
         + "WHERE p.user.id = :userId AND p.usedAt IS NULL")
    int invalidarPendientesDe(@Param("userId") UUID userId);
}
