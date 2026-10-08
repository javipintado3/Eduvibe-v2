package com.eduvibe.dto.registration;

import java.time.Instant;
import java.util.UUID;

import com.eduvibe.model.RegistrationRequest;

/**
 * Solicitud de registro tal y como la ve la administración.
 */
public record RegistrationRequestResponse(
        UUID id,
        String name,
        String email,
        String message,
        String status,
        Instant verifiedAt,
        Instant reviewedAt,
        Instant createdAt) {

    public static RegistrationRequestResponse de(RegistrationRequest solicitud) {
        return new RegistrationRequestResponse(
                solicitud.getId(),
                solicitud.getName(),
                solicitud.getEmail(),
                solicitud.getMessage(),
                solicitud.getStatus().getValor(),
                solicitud.getVerifiedAt(),
                solicitud.getReviewedAt(),
                solicitud.getCreatedAt());
    }
}
