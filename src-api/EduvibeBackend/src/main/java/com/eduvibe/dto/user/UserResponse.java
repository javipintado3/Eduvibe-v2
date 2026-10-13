package com.eduvibe.dto.user;

import java.time.Instant;
import java.util.UUID;

import com.eduvibe.model.User;

/**
 * Cómo viaja un usuario hacia el cliente.
 *
 * Es un DTO y no la entidad porque la entidad lleva el hash de la contraseña y
 * relaciones perezosas: devolverla directamente filtraría lo primero y
 * provocaría consultas inesperadas al serializar lo segundo.
 */
public record UserResponse(
        UUID id,
        String email,
        String name,
        String role,
        String status,
        String avatarUrl,
        UUID organizationId,
        Instant createdAt,
        boolean totpEnabled) {

    public static UserResponse of(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getRole().getValue(),
                user.getStatus().getValue(),
                user.getAvatarUrl(),
                user.getOrganization().getId(),
                user.getCreatedAt(),
                user.isTotpEnabled());
    }
}
