package com.eduvibe.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.eduvibe.model.TotpRecoveryCode;

public interface TotpRecoveryCodeRepository extends JpaRepository<TotpRecoveryCode, UUID> {

    /** Un código solo sirve si sigue sin gastar. */
    Optional<TotpRecoveryCode> findByUserIdAndCodeHashAndUsedAtIsNull(UUID userId, String codeHash);

    /** Al generar un juego nuevo, o al desactivar el 2FA, los anteriores dejan de valer. */
    @Modifying
    @Query("DELETE FROM TotpRecoveryCode c WHERE c.user.id = :userId")
    int borrarDe(@Param("userId") UUID userId);
}
