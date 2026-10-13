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

    public static RegistrationRequestResponse of(RegistrationRequest request) {
        return new RegistrationRequestResponse(
                request.getId(),
                request.getName(),
                request.getEmail(),
                request.getMessage(),
                request.getStatus().getValue(),
                request.getVerifiedAt(),
                request.getReviewedAt(),
                request.getCreatedAt());
    }
}
