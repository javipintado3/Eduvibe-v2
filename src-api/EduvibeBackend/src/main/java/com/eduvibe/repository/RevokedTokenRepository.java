package com.eduvibe.repository;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.eduvibe.model.RevokedToken;

public interface RevokedTokenRepository extends JpaRepository<RevokedToken, UUID> {

    @Modifying
    @Query("DELETE FROM RevokedToken r WHERE r.userId = :userId")
    int borrarDe(@Param("userId") UUID userId);

    /** Retención: un token revocado que ya habría caducado no hace falta recordarlo. */
    @Modifying
    @Query("DELETE FROM RevokedToken r WHERE r.expiresAt < :antesDe")
    int borrarCaducadosAntesDe(@Param("antesDe") Instant antesDe);
}
