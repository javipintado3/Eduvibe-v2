package com.eduvibe.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.eduvibe.model.User;
import com.eduvibe.model.enums.UserRole;
import com.eduvibe.model.enums.UserStatus;

/**
 * El listado con filtros opcionales del panel de administración se resuelve con
 * Specifications (ver {@link com.eduvibe.repository.spec.UserSpecifications}),
 * de ahí JpaSpecificationExecutor.
 */
public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

    /** Cuántas personas con ese rol y estado hay en el centro; para no dejarlo sin administración. */
    long countByOrganizationIdAndRoleAndStatus(UUID organizationId, UserRole role, UserStatus status);

    /**
     * El email es único en toda la plataforma (ver V2__email_unico_global.sql),
     * así que identifica a una sola persona sin necesidad de indicar el centro.
     */
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    /** Para comprobar unicidad al editar sin que la propia cuenta se choque consigo misma. */
    boolean existsByEmailAndIdNot(String email, UUID id);

    /** Para el resumen de perfil de administración: usuarios por rol. */
    long countByOrganizationIdAndRole(UUID organizationId, UserRole role);

    /** Cuentas todavía sin aceptar su invitación. */
    long countByOrganizationIdAndStatus(UUID organizationId, UserStatus status);
}
