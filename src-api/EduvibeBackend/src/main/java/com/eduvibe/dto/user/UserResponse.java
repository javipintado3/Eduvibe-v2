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

    public static UserResponse de(User usuario) {
        return new UserResponse(
                usuario.getId(),
                usuario.getEmail(),
                usuario.getName(),
                usuario.getRole().getValor(),
                usuario.getStatus().getValor(),
                usuario.getAvatarUrl(),
                usuario.getOrganization().getId(),
                usuario.getCreatedAt(),
                usuario.isTotpEnabled());
    }
}
