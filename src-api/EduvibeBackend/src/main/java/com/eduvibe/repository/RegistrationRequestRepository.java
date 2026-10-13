package com.eduvibe.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.eduvibe.model.RegistrationRequest;
import com.eduvibe.model.enums.RegistrationStatus;

public interface RegistrationRequestRepository extends JpaRepository<RegistrationRequest, UUID> {

    /** Se busca por el hash: el token en claro nunca llega a la base de datos. */
    Optional<RegistrationRequest> findByTokenHash(String tokenHash);

    /** La solicitud abierta (sin verificar o pendiente) de un email, si la hay. */
    Optional<RegistrationRequest> findFirstByEmailAndStatusIn(String email, Collection<RegistrationStatus> statuses);

    /** ¿Se rechazó a este email hace poco? Para no dejar que reintente en bucle. */
    boolean existsByEmailAndStatusAndReviewedAtAfter(String email, RegistrationStatus status, Instant from);

    /** Peticiones hechas desde una IP desde el instante indicado; para el límite por IP. */
    long countByIpAddressAndCreatedAtAfter(String ipAddress, Instant from);

    /** Peticiones de toda la plataforma desde el instante indicado; tope global anti-inundación. */
    long countByCreatedAtAfter(Instant from);

    Page<RegistrationRequest> findByOrganizationIdAndStatusOrderByCreatedAtAsc(UUID organizationId, RegistrationStatus status,
                                                            Pageable pageable);

    long countByOrganizationIdAndStatus(UUID organizationId, RegistrationStatus status);

    /**
     * Borra las solicitudes que nunca se verificaron y ya pasaron de fecha: son
     * basura (correos ajenos, bots) y no deben acumularse.
     */
    @Modifying
    @Query("DELETE FROM RegistrationRequest r WHERE r.status = :estado AND r.createdAt < :antes")
    int deleteUnverifiedOlderThan(@Param("estado") RegistrationStatus status, @Param("antes") Instant before);
}
