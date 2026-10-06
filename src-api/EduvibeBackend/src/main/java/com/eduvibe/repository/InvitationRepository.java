package com.eduvibe.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.eduvibe.model.Invitation;

public interface InvitationRepository extends JpaRepository<Invitation, UUID> {

    @Modifying
    @Query("DELETE FROM Invitation i WHERE i.user.id = :userId")
    int borrarDe(@Param("userId") UUID userId);

    /** Se busca por el hash: el token en claro nunca llega a la base de datos. */
    Optional<Invitation> findByTokenHash(String tokenHash);

    List<Invitation> findByUserIdOrderByCreatedAtDesc(UUID userId);

    /**
     * Invalida las invitaciones que siguen vivas de un usuario. Se llama al
     * emitir una nueva, para que reenviar la invitación deje inservible el
     * enlace anterior en lugar de dejar dos válidos a la vez.
     */
    @Modifying
    @Query("UPDATE Invitation i SET i.usedAt = CURRENT_TIMESTAMP "
         + "WHERE i.user.id = :userId AND i.usedAt IS NULL")
    int invalidarPendientesDe(@Param("userId") UUID userId);
}
